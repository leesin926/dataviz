package com.dataviz.openapi.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import com.dataviz.openapi.service.ApiLogService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对外回调的验签与限流。签名形状见 {@link OpenApiSignature}。
 * <p>每一道回调都落一行 {@code openapi_log}（拒绝与放行共用 {@link #recordAudit}，
 * 口径同 {@code OpenApiController#recordRefusal}）：拒绝本身才是运维要看的信号，不写就等于给运维页
 * 造了一个"永远不会变脏"的历史；反过来只记拒绝，则成功的一天在账上不存在。
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
    private final ApiLogService apiLogService;

    /**
     * 与网关 {@code gateway.rate-limit.trusted-proxy-hops} 同一口径：默认 0 只信 TCP 对端。
     * XFF 是客户端可自带头，配 >0 意味着"确实前置了 N 层代理、每层都往末尾追加对端"。
     */
    @Value("${openapi.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String appKey = request.getHeader(OpenApiSignature.HEADER_APP_KEY);
        String signature = request.getHeader(OpenApiSignature.HEADER_SIGNATURE);
        String timestamp = request.getHeader(OpenApiSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(OpenApiSignature.HEADER_NONCE);

        // 审计里的 clientId/appName 只在"钥匙确实验过"之后填：后面每个 reject 传 app 或 null，
        // 判据就是有没有越过 OpenApiSignature.matches —— 未验签就认领归属，等于让调用方自称是谁。
        if (isBlank(appKey) || isBlank(signature) || isBlank(timestamp) || isBlank(nonce)) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing authentication headers", "缺少 X-App-Key / X-Timestamp / X-Nonce / X-Signature",
                    null, startTime);
            return;
        }

        if (!isCurrent(timestamp)) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Request expired",
                    "时间戳超出允许偏差", null, startTime);
            return;
        }

        LambdaQueryWrapper<OpenApiApp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiApp::getAppKey, appKey)
               .eq(OpenApiApp::getStatus, 1);
        OpenApiApp app = openApiAppMapper.selectOne(wrapper);

        if (app == null) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid AppKey",
                    "AppKey 不存在或已停用", null, startTime);
            return;
        }

        CachedBodyHttpServletRequest wrapped;
        try {
            wrapped = new CachedBodyHttpServletRequest(request, MAX_BODY_BYTES);
        } catch (CachedBodyHttpServletRequest.BodyTooLargeException e) {
            // 应用记录是查到了，但密钥还没验过：归属留空（见类内 reject 的判据说明）
            reject(request, response, 413, "Request body too large", "请求体超过签名上限", null, startTime);
            return;
        }

        String canonical = OpenApiSignature.canonicalString(
                wrapped.getMethod(),
                OpenApiSignature.pathWithQuery(wrapped.getRequestURI(), wrapped.getQueryString()),
                timestamp, nonce, wrapped.getCachedBody());

        if (!OpenApiSignature.matches(app.getAppSecret(), canonical, signature)) {
            log.warn("Openapi signature mismatch: appKey={}, path={}", appKey, wrapped.getRequestURI());
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid Signature",
                    "签名校验未通过", null, startTime);
            return;
        }

        // 先验签再登记 nonce：否则未授权的请求就能占用 nonce、把合法请求挤掉
        NonceCheck nonceState = checkNonce(appKey, nonce);
        if (nonceState == NonceCheck.REPLAYED) {
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Replayed request",
                    "nonce 在重放窗口内重复出现", app, startTime);
            return;
        }
        if (nonceState == NonceCheck.STORE_UNAVAILABLE) {
            // fail-closed 是对的，但文案不能沿用"重放"：那是把"我们的 Redis 挂了"说成"你在重放"，
            // 调用方查半天查不到自己的问题（503 也更诚实——这不是授权结论，是防护不可用）。
            reject(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Nonce store unavailable",
                    "重放防护暂不可用（nonce 存储不可达），已按 fail-closed 拒绝", app, startTime);
            return;
        }

        // 限流是可用性护栏不是授权判据：Redis 不可用时放行并告警，
        // 而 nonce 是防重放判据：Redis 不可用时拒绝（宁可拒外部集成，不能悄悄重放）。
        if (isRateLimited(request, app, response, startTime)) {
            return;
        }

        if (!isWhitelisted(app.getIpWhitelist(), resolveClientIp(request))) {
            reject(request, response, HttpServletResponse.SC_FORBIDDEN, "IP not in whitelist",
                    "来源 IP 不在白名单", app, startTime);
            return;
        }

        request.setAttribute("appId", app.getId());
        request.setAttribute("tenantId", app.getTenantId());
        // 默认按"没走完"记 500：doFilter 抛出时 response.getStatus() 仍是 200（容器要到外层才写错误页），
        // 不这么写就会把业务炸掉的回调记成成功 —— 审计最坏的就是这种错方向的账。
        int outcome = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(wrapped, response);
            outcome = response.getStatus();
        } finally {
            // 放行的这一程也要留痕：原来只有拒绝写日志，于是运维页看到的是"这个调用方只会失败"，
            // 成功的一天在账上根本不存在。归属在此处已经过了验签，可以放心带 client_id。
            recordAudit(wrapped, outcome, app, startTime);
        }
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

    /** nonce 三道结论分开：通过 / 重放 / 存储不可用。原来把后两者合成"重放"，等于把自家 Redis 故障推给调用方。 */
    private enum NonceCheck { ACCEPTED, REPLAYED, STORE_UNAVAILABLE }

    private NonceCheck checkNonce(String appKey, String nonceValue) {
        String key = NONCE_KEY_PREFIX + appKey + ":" + nonceValue;
        try {
            Boolean first = cacheHelper.setIfAbsent(key, "1",
                    OpenApiSignature.SKEW_SECONDS * 2, TimeUnit.SECONDS);
            return Boolean.FALSE.equals(first) ? NonceCheck.REPLAYED : NonceCheck.ACCEPTED;
        } catch (Exception e) {
            log.error("Openapi nonce guard unavailable, rejecting request to stay fail-closed: appKey={}", appKey, e);
            return NonceCheck.STORE_UNAVAILABLE;
        }
    }

    private boolean isRateLimited(HttpServletRequest request, OpenApiApp app, HttpServletResponse response,
                                  long startTime) throws IOException {
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
        // 审计在 reject 内部完成：这条 429 的响应体也只由 reject 写一次，不另开写法
        reject(request, response, 429, "Rate limit exceeded", "超过应用配额 " + limit + " 次/分钟", app, startTime);
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
        String socketIp = request.getRemoteAddr();
        if (trustedProxyHops <= 0) {
            return socketIp;
        }
        List<String> forwarded = splitForwardedFor(request.getHeader("X-Forwarded-For"));
        int clientIndex = forwarded.size() - trustedProxyHops;
        if (clientIndex < 0 || clientIndex >= forwarded.size() || forwarded.get(clientIndex).isEmpty()) {
            // 代理链比声明的跳数短（头没带上/被剥掉/那一段写的是 unknown）⇒ 退回不可伪造的 TCP 对端
            return socketIp;
        }
        return forwarded.get(clientIndex);
    }

    /** 逗号拆分并 trim；"unknown" 记为空串，让下标运算保持诚实（与 {@code OpenApiController} 同一条规矩） */
    private static List<String> splitForwardedFor(String headerValue) {
        List<String> entries = new ArrayList<>();
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return entries;
        }
        for (String entry : headerValue.split(",")) {
            String value = entry.trim();
            entries.add("unknown".equalsIgnoreCase(value) ? "" : value);
        }
        return entries;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 拒绝的统一出口：先落审计、再写响应。
     * <p>三处刻意保守，与 {@code OpenApiController#recordRefusal} 同口径：
     * ① {@code verifiedApp} 只在密钥已经通过 {@link OpenApiSignature#matches} 之后传入（重放、限流、
     * 白名单三道），其余各点传 {@code null} —— 钥匙都没验对就把 appKey 对应的 id/name 写进
     * {@code client_id}，等于让外部自己声称归属；AppKey 查到了但没验签（413、签名不符）同样留空。
     * ② 不写 {@code requestParams} —— 回调体、签名、nonce、appSecret 一个都不进日志列，
     * 抄进去就是二次泄漏。
     * ③ 审计异常只 {@code warn} 且只带异常消息，绝不改变对外的状态码；响应体也仍由本方法写一次。
     * <p>ip 用 {@link #resolveClientIp(HttpServletRequest)}：默认只信 TCP 对端，不重开 XFF 最左值。
     */
    private void reject(HttpServletRequest request, HttpServletResponse response, int status,
                        String error, String message, OpenApiApp verifiedApp, long startTime) throws IOException {
        recordAudit(request, status, verifiedApp, startTime);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message
                + "\",\"error\":\"" + error + "\"}");
    }

    /**
     * 审计留痕的唯一写法（拒绝与放行共用）：归属只在密钥真验过之后带（{@code maybeApp} 为 null 就留空），
     * 永不写 {@code requestParams}（回调体、签名、nonce、appSecret 抄进日志列就是二次泄漏），
     * 写失败只降级为告警、绝不改变对外的状态码与响应体。
     * <p>ip 用 {@link #resolveClientIp(HttpServletRequest)}：默认只信 TCP 对端，不重开 XFF 最左值。</p>
     */
    private void recordAudit(HttpServletRequest request, int status, OpenApiApp maybeApp, long startTime) {
        try {
            apiLogService.saveLog(OpenApiLog.builder()
                    .clientId(maybeApp == null ? null : maybeApp.getId())
                    // 归属只在验过签名之后带：maybeApp 为 null 就是外部自己声明的 appKey，
                    // 那时连租户都不能写，否则拒绝记录里会出现一个"谁都能自称"的租户号
                    .tenantId(maybeApp == null ? null : maybeApp.getTenantId())
                    .appName(maybeApp == null ? null : maybeApp.getAppName())
                    .apiPath(request.getRequestURI())
                    .method(request.getMethod())
                    .responseCode(status)
                    .executionTime(System.currentTimeMillis() - startTime)
                    .ip(resolveClientIp(request))
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("开放接口回调留痕写入失败（不影响对外响应）: {}", e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/openapi/callback");
    }
}
