package com.dataviz.openapi.service;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.openapi.config.OpenApiProxyProperties;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.dto.ProxyForwardResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 开放接口代理的转发判据：把外部给的 {@code apiPath} 解析成「服务代码 + 白名单路径」，
 * 只有命中 {@link OpenApiProxyProperties} 显式配置的目标才用内部口令直连转发。
 * <p>SSRF 口径对齐 alert-service 的 {@code OutboundUrlGuard}：地址来源不能是请求体，
 * 这里是配置白名单；也刻意<b>不</b>走注册中心 —— 转发绕开网关直连兄弟服务的 localhost 端口
 * （路由表见 gateway-service 的 application.yml），一旦允许调用方给 host/port 就变成内网任意访问。
 * <p>只放行白名单内的 GET/POST：POST 的存在是因为 datasource 的内部只读查询端点就映射成 POST
 * （见 {@code InternalDatasourceController}），读不读由白名单路径把关，不由 HTTP 动词把关；
 * PUT/DELETE/PATCH 等改动类动词一律拒绝，内部端点里没有需要代理的只读写动词。
 * <p>上游状态原样上报：2xx 才可能算成功，4xx/5xx 连同响应体如实返回，绝不塌成"转发生成功"。
 */
@Slf4j
@Service
public class OpenApiProxyService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final OpenApiProxyProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public OpenApiProxyService(OpenApiProxyProperties properties,
                               RestTemplateBuilder builder,
                               ObjectMapper objectMapper,
                               @Value("${internal.api.token:}") String internalToken) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
        // 自建而非注入共享 bean：口径同 DatasourceQueryClient —— 代理在用户请求线程上同步等待，
        // 超时必须有界且比通用 bean 更短，共用会让"谁注入了哪个"变成隐式契约。
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    /**
     * 校验并转发一次代理调用。任何"不该转发"的情形都抛 {@link BizException}，
     * 消息点名缺了什么（服务代码未配置 / 路径不在白名单 / 该服务没有内部端点 / 口令未配置）。
     */
    public ProxyForwardResult forward(ApiProxyRequestDTO dto) {
        String apiPath = requirePath(dto.getApiPath());
        String method = normalizeMethod(dto.getMethod());
        OpenApiProxyProperties.Target target = requireTarget(apiPath);
        String upstreamUrl = buildUpstreamUrl(apiPath, dto, method, target);

        if (internalToken == null || internalToken.trim().isEmpty()) {
            // 与 InternalApiInterceptor 同一 fail-closed 口径：口令没配就拒打，别把空口令发出去换 403
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "内部口令未配置：openapi-service 与目标服务两边的 internal.api.token 必须配成同一个值");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        log.info("代理转发: apiPath={} -> {}", apiPath, upstreamUrl);
        try {
            ResponseEntity<String> response;
            if ("GET".equals(method)) {
                response = restTemplate.exchange(upstreamUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            } else {
                response = restTemplate.exchange(upstreamUrl, HttpMethod.POST,
                        new HttpEntity<>(resolveJsonBody(dto), headers), String.class);
            }
            return new ProxyForwardResult(response.getStatusCodeValue(), parseBody(response.getBody()));
        } catch (HttpStatusCodeException e) {
            // 上游 4xx/5xx：状态与响应体如实上报，由调用方判断，这里不降级不美化
            log.warn("代理上游返回异常状态: url={}, status={}", upstreamUrl, e.getRawStatusCode());
            return new ProxyForwardResult(e.getRawStatusCode(), parseBody(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "无法访问目标服务 " + serviceCodeOf(apiPath) + "（" + upstreamUrl + "）: " + e.getMessage());
        }
    }

    private String requirePath(String apiPath) {
        if (apiPath == null || apiPath.trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "apiPath 不能为空：代理按 /api/<服务代码>/... 定位白名单目标");
        }
        String path = apiPath.trim();
        if (!path.startsWith("/api/") || path.length() <= "/api/".length()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "apiPath 必须以 /api/<服务代码>/ 开头（网关对外口径），拒绝转发: " + apiPath);
        }
        return path;
    }

    private String normalizeMethod(String rawMethod) {
        String method = rawMethod == null ? "" : rawMethod.trim().toUpperCase();
        if (method.isEmpty()) {
            method = "GET";
        }
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "代理只放行 GET/POST（POST 仅限白名单内的内部查询端点），拒绝 " + method);
        }
        return method;
    }

    private OpenApiProxyProperties.Target requireTarget(String apiPath) {
        String serviceCode = serviceCodeOf(apiPath);
        OpenApiProxyProperties.Target target = properties.getServices().get(serviceCode);
        if (target == null || target.getBaseUrl() == null || target.getBaseUrl().trim().isEmpty()) {
            List<String> configured = new ArrayList<String>(properties.getServices().keySet());
            throw new BizException(ErrorCode.FORBIDDEN,
                    "服务代码 [" + serviceCode + "] 不是可调用的开放接口目标；已配置的目标: "
                            + (configured.isEmpty() ? "（无，见 openapi.proxy.services）" : configured));
        }
        if (target.getPaths().isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "服务 [" + serviceCode + "] 未暴露任何内部只读端点：先在该服务加 /internal/ 端点，"
                            + "再配置 openapi.proxy.services." + serviceCode + ".paths，代理不会去打注定被拒的路径");
        }
        for (String pattern : target.getPaths()) {
            if (PATH_MATCHER.match(pattern, apiPath)) {
                return target;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "路径 [" + apiPath + "] 不在服务 [" + serviceCode + "] 的可调用白名单内；"
                        + "白名单: " + target.getPaths()
                        + "（新增需在 openapi.proxy.services." + serviceCode + ".paths 配置）");
    }

    private String buildUpstreamUrl(String apiPath, ApiProxyRequestDTO dto, String method,
                                    OpenApiProxyProperties.Target target) {
        String upstreamPath = apiPath;
        if (target.isStripApiPrefix()) {
            // 网关对 user-service 配了 StripPrefix=1，其控制器映射在 /user/internal；直连必须剥掉 /api
            upstreamPath = apiPath.substring("/api".length());
        }
        String base = target.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + upstreamPath;
        if ("GET".equals(method) && dto.getParams() != null && !dto.getParams().isEmpty()) {
            url = url + "?" + toQueryString(dto.getParams());
        }
        return url;
    }

    private String serviceCodeOf(String apiPath) {
        int start = "/api/".length();
        int end = apiPath.indexOf('/', start);
        return end < 0 ? apiPath.substring(start) : apiPath.substring(start, end);
    }

    /** 排序后拼接，保证同样参数得到同样 URL（日志与测试都依赖这一点） */
    private String toQueryString(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(params).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(entry.getValue())));
        }
        return sb.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** POST 的 JSON 体：优先用显式 body；没给就把 params 整体序列化过去（datasource 内部查询就吃这个形状） */
    private String resolveJsonBody(ApiProxyRequestDTO dto) {
        if (dto.getBody() != null && !dto.getBody().trim().isEmpty()) {
            return dto.getBody();
        }
        if (dto.getParams() != null) {
            try {
                return objectMapper.writeValueAsString(dto.getParams());
            } catch (Exception e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "params 无法序列化为 JSON 请求体: " + e.getMessage());
            }
        }
        return "{}";
    }

    /** 上游响应体尽量还原成结构化对象，解析不动就原样给字符串 —— 两种都不加工、不兜底 */
    private Object parseBody(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }
}
