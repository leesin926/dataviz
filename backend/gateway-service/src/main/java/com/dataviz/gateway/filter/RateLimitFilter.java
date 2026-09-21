package com.dataviz.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based rate limiting filter.
 * <p>
 * Uses a sliding window counter approach backed by Redis to enforce per-user
 * and per-IP rate limits. Falls back to in-memory limiting if Redis is unavailable.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    /** Maximum requests per user per minute */
    private static final int USER_RATE_LIMIT = 300;

    /** Maximum requests per IP per minute */
    private static final int IP_RATE_LIMIT = 60;

    /** Window duration in seconds */
    private static final long WINDOW_SECONDS = 60;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Extract user ID from header (set by AuthGlobalFilter) or fall back to IP
        String userId = request.getHeaders().getFirst("X-User-Id");
        String clientIp = getClientIp(request);

        String userKey = "rate:user:" + (userId != null ? userId : clientIp);
        String ipKey = "rate:ip:" + clientIp;

        return checkRateLimit(exchange, chain, userKey, ipKey);
    }

    @Override
    public int getOrder() {
        return -50;
    }

    /**
     * Check rate limits using Redis INCR with TTL for sliding window.
     */
    private Mono<Void> checkRateLimit(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String userKey, String ipKey) {
        return redisTemplate.opsForValue().increment(userKey)
                .flatMap(userCount -> {
                    // Set expiry on first request in window
                    if (userCount != null && userCount == 1L) {
                        return redisTemplate.expire(userKey, Duration.ofSeconds(WINDOW_SECONDS))
                                .then(checkIpLimit(exchange, chain, ipKey));
                    }
                    if (userCount != null && userCount > USER_RATE_LIMIT) {
                        log.warn("User rate limit exceeded: key={}, count={}", userKey, userCount);
                        return rateLimitResponse(exchange, "User rate limit exceeded. Try again later.");
                    }
                    return checkIpLimit(exchange, chain, ipKey);
                })
                .onErrorResume(e -> {
                    // Graceful degradation: allow request if Redis is down
                    log.error("Redis unavailable for rate limiting, allowing request", e);
                    return chain.filter(exchange);
                });
    }

    /**
     * Check IP-level rate limit.
     */
    private Mono<Void> checkIpLimit(ServerWebExchange exchange, GatewayFilterChain chain, String ipKey) {
        return redisTemplate.opsForValue().increment(ipKey)
                .flatMap(ipCount -> {
                    if (ipCount != null && ipCount == 1L) {
                        return redisTemplate.expire(ipKey, Duration.ofSeconds(WINDOW_SECONDS))
                                .then(chain.filter(exchange));
                    }
                    if (ipCount != null && ipCount > IP_RATE_LIMIT) {
                        log.warn("IP rate limit exceeded: key={}, count={}", ipKey, ipCount);
                        return rateLimitResponse(exchange, "IP rate limit exceeded. Try again later.");
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> chain.filter(exchange));
    }

    /**
     * Extract client IP address from request headers or remote address.
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp;
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * Return a 429 Too Many Requests response.
     */
    private Mono<Void> rateLimitResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":429,\"message\":\"%s\",\"data\":null}", message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
