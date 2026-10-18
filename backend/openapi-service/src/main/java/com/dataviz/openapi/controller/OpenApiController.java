package com.dataviz.openapi.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.service.ApiAuthService;
import com.dataviz.openapi.service.ApiLogService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 对外统一入口。这条端点<b>不上用户权限码</b>（R1 的 C 档：建议不套）：
 * 它的主体是外部应用（appKey/appSecret 在请求体里自证），套 {@code openapi:*} 等于要求外部系统
 * 先有平台账号会话，任何真实集成都会当场断。
 * <p>两条必须一起记住的现状：
 * ① {@code routeRequest} 还是 TODO 占位，只回显路径与方法，并没有真的转发 —— 所以它现在既不产生
 * 数据风险，也谈不上 API-33 那类 SSRF；等真接转发时必须回来补目标白名单。
 * ② {@code getClientIp} 直接取 {@code X-Forwarded-For} 最左值，而那个头是客户端自写的；
 * 这个值会进 {@code open_api_log.ip} 当审计字段。同口径的修法已在
 * {@code OpenApiAuthFilter.resolveClientIp}（默认只信 TCP 对端，按可信跳数从右往左数）落过，
 * 这里还没改，因为它属于"占位接口要不要现在当真"的同一批决策。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi/proxy")
@RequiredArgsConstructor
public class OpenApiController {

    private final ApiAuthService apiAuthService;
    private final ApiLogService apiLogService;

    /**
     * Proxy endpoint that validates and forwards requests to internal services.
     * All external API calls go through this single endpoint.
     */
    @PostMapping("/invoke")
    public R<Object> proxy(@RequestBody ApiProxyRequestDTO dto, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String clientIp = getClientIp(request);

        // Authenticate
        OpenApiClient client = apiAuthService.authenticate(dto.getAppKey(), dto.getAppSecret(), clientIp);

        Object result = null;
        int responseCode = 200;
        try {
            // Route to internal service based on apiPath
            result = routeRequest(dto, client);
        } catch (Exception e) {
            responseCode = 500;
            log.error("Proxy request failed: apiPath={}, error={}", dto.getApiPath(), e.getMessage());
            return R.fail("Internal error: " + e.getMessage());
        } finally {
            // Log the API call
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

        return R.ok(result);
    }

    /**
     * Route the request to the appropriate internal service
     */
    private Object routeRequest(ApiProxyRequestDTO dto, OpenApiClient client) {
        String apiPath = dto.getApiPath();
        log.info("Routing API request: appKey={}, path={}, method={}", client.getAppKey(), apiPath, dto.getMethod());

        // TODO: Implement actual routing to internal services
        // This would typically use a service registry or direct HTTP calls
        // to forward the request to the appropriate microservice
        Map<String, Object> result2 = new HashMap<>();
        result2.put("path", apiPath);
        result2.put("method", dto.getMethod());
        result2.put("message", "Request routed successfully. Internal service integration pending.");
        result2.put("clientApp", client.getAppName());
        return result2;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Take first IP if multiple
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
