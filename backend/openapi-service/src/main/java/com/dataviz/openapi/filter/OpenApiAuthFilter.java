package com.dataviz.openapi.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import com.dataviz.openapi.support.CachedBodyHttpServletRequest;
import com.dataviz.openapi.support.OpenApiSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对外回调的验签与限流。签名形状见 {@link OpenApiSignature}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiAuthFilter extends OncePerRequestFilter {

    /** 签名要覆盖整个请求体，所以给体积设上限；回调载荷是控制消息，不是文件。 */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private static final String NONCE_KEY_PREFIX = "openapi:nonce:";
    private static final String RATE_KEY_PREFIX = "openapi:rate:";
    private static final int DEFAULT_RATE_LIMIT = 60;

    private final OpenApiAppMapper openApiAppMapper;
    private final CacheHelper cacheHelper;

    /**
     * 与网关 {@code gateway.rate-limit.trusted-proxy-hops} 同一口径：默认 0 只信 TCP 对端。
     * XFF 是客户端可自带头，配 >0 意味着"确实前置了 N 层代理、每层都往末尾追加对端"。
     */
    @Value("${openapi.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String appKey = request.getHeader(OpenApiSignature.HEADER_APP_KEY);
        String signature = request.getHeader(OpenApiSignature.HEADER_SIGNATURE);
        String timestamp = request.getHeader(OpenApiSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(OpenApiSignature.HEADER_NONCE);

        if (isBlank(appKey) || isBlank(signature) || isBlank(timestamp) || isBlank(nonce)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing authentication headers", "缺少 X-App-Key / X-Timestamp / X-Nonce / X-Signature");
            return;
        }

        if (!isCurrent(timestamp)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Request expired", "时间戳超出允许偏差");
            return;
        }

        LambdaQueryWrapper<OpenApiApp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiApp::getAppKey, appKey)
               .eq(OpenApiApp::getStatus, 1);
        OpenApiApp app = openApiAppMapper.selectOne(wrapper);

        if (app == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid AppKey", "AppKey 不存在或已停用");
            return;
        }

        CachedBodyHttpServletRequest wrapped;
        try {
            wrapped = new CachedBodyHttpServletRequest(request, MAX_BODY_BYTES);
        } catch (CachedBodyHttpServletRequest.BodyTooLargeException e) {
            reject(response, 413, "Request body too large", "请求体超过签名上限");
            return;
        }

        String canonical = OpenApiSignature.canonicalString(
                wrapped.getMethod(),
                OpenApiSignature.pathWithQuery(wrapped.getRequestURI(), wrapped.getQueryString()),
                timestamp, nonce, wrapped.getCachedBody());

        if (!OpenApiSignature.matches(app.getAppSecret(), canonical, signature)) {
            log.warn("Openapi signature mismatch: appKey={}, path={}", appKey, wrapped.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid Signature", "签名校验未通过");
            return;
        }

        // 先验签再登记 nonce：否则未授权的请求就能占用 nonce、把合法请求挤掉
        if (isReplayedNonce(appKey, nonce)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Replayed request", "nonce 在重放窗口内重复出现");
            return;
        }

        // 限流是可用性护栏不是授权判据：Redis 不可用时放行并告警，
        // 而 nonce 是防重放判据：Redis 不可用时拒绝（宁可拒外部集成，不能悄悄重放）。
        if (isRateLimited(app, response)) {
            return;
        }

        if (!isWhitelisted(app.getIpWhitelist(), resolveClientIp(request))) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "IP not in whitelist", "来源 IP 不在白名单");
            return;
        }

        request.setAttribute("appId", app.getId());
        request.setAttribute("tenantId", app.getTenantId());
        filterChain.doFilter(wrapped, response);
    }

    private boolean isCurrent(String timestamp) {
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        return Math.abs(now - epochSeconds) <= OpenApiSignature.SKEW_SECONDS;
    }

    private boolean isReplayedNonce(String appKey, String nonce) {
        String key = NONCE_KEY_PREFIX + appKey + ":" + nonce;
        try {
            Boolean first = cacheHelper.setIfAbsent(key, "1",
                    OpenApiSignature.SKEW_SECONDS * 2, TimeUnit.SECONDS);
            return Boolean.FALSE.equals(first);
        } catch (Exception e) {
            log.error("Openapi nonce guard unavailable, rejecting request to stay fail-closed: appKey={}", appKey, e);
            return true;
        }
    }

    private boolean isRateLimited(OpenApiApp app, HttpServletResponse response) throws IOException {
        int limit = app.getRateLimit() == null || app.getRateLimit() <= 0
                ? DEFAULT_RATE_LIMIT : app.getRateLimit();
        long windowSeconds = 60L;
        String key = RATE_KEY_PREFIX + app.getId() + ":" + System.currentTimeMillis() / windowSeconds / 1000L;
        Long count;
        try {
            count = cacheHelper.increment(key, 1L);
            if (count != null && count == 1L) {
                cacheHelper.expire(key, windowSeconds * 2, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Openapi rate limiter unavailable, failing open: appKey={}", app.getAppKey(), e);
            return false;
        }
        if (count == null || count <= limit) {
            return false;
        }
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        reject(response, 429, "Rate limit exceeded", "超过应用配额 " + limit + " 次/分钟");
        return true;
    }

    /**
     * 白名单是逗号分隔的精确清单。原实现用 {@code ipWhitelist.contains(clientIp)}，
     * 那是子串匹配：配了 10.0.0.15 会把 10.0.0.1 也算放行。
     */
    private static boolean isWhitelisted(String ipWhitelist, String clientIp) {
        if (ipWhitelist == null || ipWhitelist.trim().isEmpty()) {
            return true;
        }
        for (String entry : ipWhitelist.split(",")) {
            if (entry.trim().equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客户端地址只采信能证的东西：默认取 TCP 对端。前置了 N 层可信代理时才从 XFF 从右往左数第 N 个取值，
     * 因为最左值是客户端自己写的。与网关 D58 的判据一致，不在服务侧另立一套。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustedProxyHops <= 0) {
            return request.getRemoteAddr();
        }
        List<String> forwarded = splitForwardedFor(request.getHeader("X-Forwarded-For"));
        int clientIndex = forwarded.size() - trustedProxyHops;
        if (clientIndex < 0 || clientIndex >= forwarded.size()) {
            return request.getRemoteAddr();
        }
        return forwarded.get(clientIndex);
    }

    private static List<String> splitForwardedFor(String headerValue) {
        List<String> entries = new ArrayList<>();
        if (headerValue == null || headerValue.isEmpty()) {
            return entries;
        }
        for (String entry : headerValue.split(",")) {
            entries.add(entry.trim());
        }
        return entries;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message
                + "\",\"error\":\"" + error + "\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/openapi/callback");
    }
}
