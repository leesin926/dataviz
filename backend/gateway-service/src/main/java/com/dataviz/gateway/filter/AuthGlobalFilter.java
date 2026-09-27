package com.dataviz.gateway.filter;

import com.dataviz.common.security.util.JwtHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Global authentication filter for all gateway requests.
 * <p>
 * Responsibilities:
 * - Validate JWT tokens from the Authorization header
 * - Skip validation for whitelisted paths (login, captcha, etc.)
 * - Forward decoded user information (X-User-Id, X-Tenant-Id) to downstream services
 * - Strip those same headers on the whitelisted paths, so "免登" never means "身份头可信"
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 标记"这次请求的 JWT 已在网关验过"，供 {@link RateLimitFilter} 判断 {@code X-User-Id} 是不是网关自己写的。
     * <p>
     * 免登白名单分支不做任何 header mutate ⇒ 那条路上 {@code X-User-Id} 是客户端自带的，拿它当限流 key 就等于
     * 让攻击者挑往哪个桶里灌额度。这个标记打在交给下游的那份 exchange 上，所以下游 filter 一定读得到。
     */
    public static final String ATTR_AUTHENTICATED = "dataviz.gateway.authenticated";

    /**
     * 网关自己负责写入的身份头。下游服务读它们 = 读网关的认证结论，所以任何一条路上都不能让客户端带着走。
     * <p>
     * 与 {@link #ATTR_AUTHENTICATED} 配套：已认证分支用 {@code header(...)} 覆盖（{@code HttpHeaders.put} 语义，
     * 天然清洗），免登分支原先直接 {@code chain.filter(exchange)} 一个字节都没动 ⇒ 那条路上的这三个头是客户端自带的。
     * {@code /api/auth/logout} 就在免登名单里，它一度因此可以被伪造成任意 userId。
     */
    private static final List<String> IDENTITY_HEADERS = Arrays.asList("X-User-Id", "X-Tenant-Id", "X-Username");

    /**
     * Whitelist of paths that do not require authentication.
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refreshToken",
            "/api/auth/captcha",
            // 短信登录两条端点：免 JWT，但身份头照样被 stripIdentityHeaders 洗掉。
            // 逐条列举而不是 /api/auth/sms/**：以后这里若加了要鉴权的端点，通配会替它免登。
            "/api/auth/sms/send",
            "/api/auth/sms/login",
            "/api/auth/sso/**",
            // 大屏分享链接：免登录展示，业务侧仅返回已发布配置
            "/api/screen/share/**",
            // 图片直读：<img>/CSS 背景带不了 Authorization 头
            "/api/file/view/**",
            // 免登可读的全局开关（后端仅放行白名单 key）
            "/api/admin/config/public/**",
            // 免登全局配置推送通道。刻意写死整条路径而不是 /api/admin/ws/**：
            // 以后新增的 WS 通道多半要鉴权，用通配等于默认替它免登。
            "/api/admin/ws/public",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**"
    );

    /**
     * 服务间内部接口：无论有没有合法 JWT 都不允许从网关进来。
     * 这类端点免 JWT（认证发生在登录之前），只靠内部口令防护，不能暴露成公网可达面。
     * <p>
     * 这里用约定通配（任意前缀 + internal 段 + 任意后缀）而不是逐条列举，方向与免登白名单相反：
     * 白名单漏配的代价是"多拦一道 401"（安全），内部端点漏配的代价是"把能读口令哈希的端点公开出去"（危险）。
     * 网关看到的是 StripPrefix 之前的完整路径，故带 /api（见 D38）。
     */
    private static final List<String> DENY_LIST = Arrays.asList(
            "/**/internal/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isDenied(path)) {
            log.warn("拒绝内部接口经网关访问: {}", path);
            return forbiddenResponse(exchange, "Internal API is not reachable through the gateway");
        }

        // Check if the path is whitelisted
        if (isWhiteListed(path)) {
            // 免登不等于免清洗：这条路没有 JWT 可解，网关写不出身份头，那就不许别人替它写
            return chain.filter(stripIdentityHeaders(exchange));
        }

        // Extract and validate the JWT token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            // Validate token and extract claims
            if (!JwtHelper.validateToken(token)) {
                log.warn("Token validation failed for path: {}", path);
                return unauthorizedResponse(exchange, "Token is expired or invalid");
            }

            String userId = String.valueOf(JwtHelper.getUserId(token));
            String tenantId = JwtHelper.getTenantId(token);
            String username = JwtHelper.getUsername(token);

            if (userId == null || tenantId == null) {
                log.warn("Token missing userId or tenantId for path: {}", path);
                return unauthorizedResponse(exchange, "Token payload is incomplete");
            }

            // Forward user context to downstream services via headers
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Username", username)
                    .build();
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            mutatedExchange.getAttributes().put(ATTR_AUTHENTICATED, Boolean.TRUE);

            log.debug("Authenticated user [{}] tenant [{}] for path: {}", userId, tenantId, path);
            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            log.error("Authentication error for path: {}", path, e);
            return unauthorizedResponse(exchange, "Authentication failed: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * Check whether the request path matches any whitelisted pattern.
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 剥掉客户端自带的身份头，返回可交给下游的那份 exchange。
     * <p>
     * 判据只放在网关这一层，且<strong>不能只在保存/登录侧校验</strong>：免登端点是任意请求都能打到的，
     * 只要下游有一个 {@code @RequestHeader("X-User-Id")}，那条路就是通的（本次的 {@code /api/auth/logout} 即为一例）。
     * 名字按 {@code equalsIgnoreCase} 挑，不依赖底层 Map 的大小写敏感性 —— HTTP 头名本来就是大小写不敏感的。
     */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        List<String> present = new ArrayList<String>(IDENTITY_HEADERS.size());
        for (String name : request.getHeaders().keySet()) {
            for (String owned : IDENTITY_HEADERS) {
                if (owned.equalsIgnoreCase(name)) {
                    present.add(name);
                    break;
                }
            }
        }
        if (present.isEmpty()) {
            return exchange;
        }
        log.warn("免登路径携带身份头，已剥除: headers={}", present);
        ServerHttpRequest stripped = request.mutate()
                .headers(headers -> {
                    for (String name : present) {
                        headers.remove(name);
                    }
                })
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private boolean isDenied(String path) {
        return DENY_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * Return a 403 Forbidden JSON response.
     */
    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":403,\"message\":\"%s\",\"data\":null}", message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Return a 401 Unauthorized JSON response.
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
