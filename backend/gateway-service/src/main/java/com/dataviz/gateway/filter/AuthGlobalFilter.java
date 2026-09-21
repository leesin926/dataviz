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
import java.util.Arrays;
import java.util.List;

/**
 * Global authentication filter for all gateway requests.
 * <p>
 * Responsibilities:
 * - Validate JWT tokens from the Authorization header
 * - Skip validation for whitelisted paths (login, captcha, etc.)
 * - Forward decoded user information (X-User-Id, X-Tenant-Id) to downstream services
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Whitelist of paths that do not require authentication.
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refreshToken",
            "/api/auth/captcha",
            "/api/auth/sso/**",
            // 大屏分享链接：免登录展示，业务侧仅返回已发布配置
            "/api/screen/share/**",
            // 图片直读：<img>/CSS 背景带不了 Authorization 头
            "/api/file/view/**",
            // 免登可读的全局开关（后端仅放行白名单 key）
            "/api/admin/config/public/**",
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
            return chain.filter(exchange);
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

            log.debug("Authenticated user [{}] tenant [{}] for path: {}", userId, tenantId, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

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
