package com.dataviz.openapi.controller;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.openapi.config.OpenApiProxyProperties;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.dto.ProxyForwardResult;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.service.ApiAuthService;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.service.OpenApiProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 对外统一入口。这条端点<b>不上用户权限码</b>（R1 的 C 档：建议不套）：
 * 它的主体是外部应用（appKey/appSecret 在请求体里自证），套 {@code openapi:*} 等于要求外部系统
 * 先有平台账号会话，任何真实集成都会当场断。
 * <p>曾经的"占位现状"两条都已落地为真实行为：
 * ① 转发实装于 {@link OpenApiProxyService}：外部给的 {@code apiPath} 只有命中
 * {@link OpenApiProxyProperties} 显式配置的「服务代码 + 只读路径白名单」才转发，
 * 目标地址永远来自配置而非请求体（SSRF 判据对齐 alert-service 的 {@code OutboundUrlGuard}，
 * 直连 localhost 兄弟端口、不走注册中心 —— 见 gateway-service 路由表）；上游状态与响应体
 * 如实透传给调用方，pc-admin「开放接口调用日志」自此有真实数据源。
 * ② 客户端地址见 {@link #resolveClientIp}：不再取 {@code X-Forwarded-For} 最左值 —— 那是
 * 客户端自写的，而该值进 {@code openapi_log.ip} 当审计字段。与网关 {@code RateLimitFilter}、
 * 本服务 {@code OpenApiAuthFilter} 同口径：默认只信 TCP 对端，显式配置可信跳数才从右往左数。
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi/proxy")
@RequiredArgsConstructor
public class OpenApiController {

    private final ApiAuthService apiAuthService;
    private final ApiLogService apiLogService;
    private final OpenApiProxyService openApiProxyService;
    private final OpenApiProxyProperties proxyProperties;

    /**
     * 对外代理入口：鉴权 → 白名单校验并转发 → 无论成败都落一行 openapi_log 审计。
     * 响应 code 如实反映结果：上游 2xx 给真实响应体；白名单拒绝、上游 4xx/5xx、服务不可达
     * 各返回真实状态码与点名原因 —— 绝不合成"转发生成功"。
     */
    @PostMapping("/invoke")
    public R<Object> proxy(@RequestBody ApiProxyRequestDTO dto, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String clientIp = resolveClientIp(request);

        // Authenticate —— 鉴权不过直接抛 BizException（与改前一致，不进审计段）
        OpenApiClient client = apiAuthService.authenticate(dto.getAppKey(), dto.getAppSecret(), clientIp);

        // 初值 500：走到审计段却没被任何分支覆写过状态的，只能是异常本身（不合成 200）
        int responseCode = ErrorCode.INTERNAL_SERVER_ERROR.getCode();
        String failureMessage = null;
        ProxyForwardResult forwarded = null;
        try {
            forwarded = openApiProxyService.forward(dto);
            responseCode = forwarded.getStatus();
        } catch (BizException e) {
            responseCode = e.getCode();
            failureMessage = e.getMessage();
            log.warn("代理调用被拒/失败: appKey={}, apiPath={}, code={}, reason={}",
                    client.getAppKey(), dto.getApiPath(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            responseCode = ErrorCode.INTERNAL_SERVER_ERROR.getCode();
            failureMessage = "代理转发异常: " + e.getMessage();
            log.error("代理转发异常: appKey={}, apiPath={}", client.getAppKey(), dto.getApiPath(), e);
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            OpenApiLog apiLog = OpenApiLog.builder()
                    .clientId(client.getId())
                    .appName(client.getAppName())
                    .apiPath(dto.getApiPath())
                    .method(dto.getMethod())
                    .requestParams(dto.getParams() != null ? dto.getParams().toString() : "")
                    .responseCode(responseCode)
                    .executionTime(executionTime)
                    .ip(clientIp)
                    .createTime(LocalDateTime.now())
                    .build();
            apiLogService.saveLog(apiLog);
        }

        if (failureMessage != null) {
            return R.fail(responseCode, failureMessage);
        }
        if (forwarded.is2xxSuccess()) {
            return R.ok(forwarded.getBody());
        }
        // 上游非 2xx：真实状态码进 R.code，消息带上游响应体摘要 —— 调用方看得见失败本身
        return R.fail(responseCode, "上游返回 " + responseCode + "：" + summarize(forwarded.getBody()));
    }

    /**
     * 客户端地址解析：默认<b>只信 TCP 对端</b>。X-Forwarded-For 最左值是客户端自写的，
     * 直接取用等于让外部把任意字符串写进 {@code openapi_log.ip} 审计字段（伪造来源、污染排查线索）。
     * 只有确实前置了 N 层可信反代、且每层往 XFF 末尾追加自己看到的对端时，才配
     * {@code openapi.proxy.trusted-proxy-hops=N} 并取从右往左第 N 项 —— 与网关 D58 的修法同口径，
     * 不在服务侧另立一套。不读 X-Real-IP：它要可信代理整体覆写才有意义，而那正是无法假设的前提。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String socketIp = request.getRemoteAddr();
        int hops = proxyProperties.getTrustedProxyHops();
        if (hops <= 0) {
            return socketIp;
        }
        List<String> forwarded = splitForwardedFor(request.getHeader("X-Forwarded-For"));
        int clientIndex = forwarded.size() - hops;
        if (clientIndex < 0 || clientIndex >= forwarded.size() || forwarded.get(clientIndex).isEmpty()) {
            // 代理链比声明的跳数短（头没带上或被剥掉）⇒ 退回不可伪造的 TCP 对端，宁保守不轻信
            return socketIp;
        }
        return forwarded.get(clientIndex);
    }

    /** 逗号拆分并 trim；"unknown" 记为空串，让下标运算保持诚实（同网关 RateLimitFilter） */
    private static List<String> splitForwardedFor(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> entries = new ArrayList<String>();
        for (String item : headerValue.split(",")) {
            String value = item.trim();
            entries.add("unknown".equalsIgnoreCase(value) ? "" : value);
        }
        return entries;
    }

    /** 上游响应体摘要：R 形状里挑 message，挑不出就压成一行原文并截断，防审计消息爆长 */
    private static String summarize(Object body) {
        if (body == null) {
            return "空响应体";
        }
        if (body instanceof Map) {
            Object message = ((Map<?, ?>) body).get("message");
            if (message != null) {
                return String.valueOf(message);
            }
        }
        String raw = String.valueOf(body).replaceAll("[\\s]+", " ").trim();
        return raw.length() <= 200 ? raw : raw.substring(0, 200) + "...";
    }
}
