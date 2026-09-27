package com.dataviz.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis-based rate limiting filter.
 * <p>
 * 固定窗口计数（Redis {@code INCR} + 首次命中时 {@code EXPIRE}），分别按用户与按客户端地址限流。
 * ⚠️ Redis 不可用时是 <strong>fail-open</strong>（{@code onErrorResume} 里直接放行，只在日志记一条）
 * ⇒ 限流与 Redis 同生共死，Redis 挂掉时这一层防护静默消失。
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

    /**
     * 网关前面有几层<strong>可信</strong>反向代理。0（默认）= 网关就是边缘节点，客户端地址只认 TCP 对端。
     * 只有确实前置了 N 层、且每层都把自己看到的对端追加进 X-Forwarded-For 时，才配 N。
     */
    @Value("${gateway.rate-limit.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // X-User-Id 只有验过 JWT 的请求才是网关写进来的，见 AuthGlobalFilter#ATTR_AUTHENTICATED
        String userId = Boolean.TRUE.equals(exchange.getAttributes().get(AuthGlobalFilter.ATTR_AUTHENTICATED))
                ? request.getHeaders().getFirst("X-User-Id")
                : null;
        String clientIp = resolveClientIp(exchange);

        String userKey = "rate:user:" + (userId != null ? userId : clientIp);
        String ipKey = "rate:ip:" + clientIp;

        return checkRateLimit(exchange, chain, userKey, ipKey);
    }

    @Override
    public int getOrder() {
        return -50;
    }

    /**
     * 固定窗口计数：Redis INCR + 首次命中时设 TTL（窗口长度 WINDOW_SECONDS）。
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
     * Extract the client address used as the rate-limit bucket key.
     * <p>
     * 默认<strong>只信 TCP 对端地址</strong>。{@code X-Forwarded-For} 是客户端可自带头，原先"有 XFF 就取最左值"的写法
     * 等于把桶的名字交给请求方：每个请求换一个值即可无限绕开额度，把某个真实 IP 写进去又能替别人把额度打光
     * （两种现象都已在报告 19.8 实测）。只有确实前置了 N 层可信反代时才按 XFF 解析 —— 每层把自己看到的对端
     * 追加到末尾，所以从左数第 {@code size - N} 项才是客户端，它左边的项全部可能是伪造的。
     * 不再读 {@code X-Real-IP}：它只能由可信代理整体覆写才有意义，而那正是本方法无法假设的前提。
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String socketIp = socketAddress(exchange.getRequest());
        if (trustedProxyHops <= 0) {
            return socketIp;
        }
        List<String> forwarded = splitForwardedFor(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        int clientIndex = forwarded.size() - trustedProxyHops;
        if (clientIndex < 0 || forwarded.get(clientIndex).isEmpty()) {
            // 代理链比声明的跳数短（头被剥掉，或请求根本没走那几层可信代理）⇒ 退回不可伪造的 socket 地址
            return socketIp;
        }
        return forwarded.get(clientIndex);
    }

    /** Split a {@code X-Forwarded-For} value into trimmed entries, keeping blanks so index arithmetic stays honest */
    private static List<String> splitForwardedFor(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] raw = headerValue.split(",");
        List<String> entries = new ArrayList<String>(raw.length);
        for (String item : raw) {
            String value = item.trim();
            entries.add("unknown".equalsIgnoreCase(value) ? "" : value);
        }
        return entries;
    }

    private static String socketAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
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
