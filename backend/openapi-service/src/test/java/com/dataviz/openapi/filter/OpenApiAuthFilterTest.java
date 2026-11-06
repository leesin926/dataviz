package com.dataviz.openapi.filter;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.support.OpenApiSignature;
import com.dataviz.openapi.test.MybatisPlusTableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回调验签链的分支覆盖。重点不在"能不能通过"，而在五件事：
 * 判据顺序（先验签后登记 nonce，否则未授权请求就能占坑挤掉合法请求）、
 * 故障口径（nonce 挂了必须拒、限流挂了可以放）、
 * 白名单是精确清单不是子串、
 * 客户端地址默认只信 TCP 对端、
 * 每一道拒绝都入 openapi_log（身份只在密钥验过之后认领，请求体/签名/nonce/密钥永不入列）。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAuthFilterTest {

    private static final String APP_KEY = "ak-unit-test";
    private static final String SECRET = "unit-test-secret";
    private static final String CALLBACK_URI = "/api/openapi/callback/order";
    private static final byte[] BODY = "{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8);

    @Mock
    private OpenApiAppMapper openApiAppMapper;

    @Mock
    private CacheHelper cacheHelper;

    @Mock
    private ApiLogService apiLogService;

    @InjectMocks
    private OpenApiAuthFilter filter;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(OpenApiApp.class);
    }

    @Test
    @DisplayName("四枚签名头缺一律拒，且不去查库")
    void should_reject401_when_headerMissing() throws Exception {
        MockHttpServletRequest request = callbackRequest();
        request.removeHeader(OpenApiSignature.HEADER_NONCE);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("X-Signature"));
        assertNull(chain.getRequest());
        verify(openApiAppMapper, never()).selectOne(anyWrapper());
    }

    @Test
    @DisplayName("时间戳不是数字或超出偏差窗口都判过期")
    void should_reject401_when_timestampStale() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        for (String timestamp : new String[]{"not-a-number",
                String.valueOf(now - OpenApiSignature.SKEW_SECONDS - 1)}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(requestWith(timestamp, "n-1"), response, chain);

            assertEquals(401, response.getStatus(), timestamp);
            assertTrue(response.getContentAsString().contains("时间戳"), timestamp);
        }
    }

    @Test
    @DisplayName("AppKey 查不到（不存在或已停用）→ 401，不验签")
    void should_reject401_when_appUnknown() throws Exception {
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(signedRequest(app(1L, SECRET), "/api/openapi/callback/order", null, BODY), response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AppKey"));
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("请求体超过签名上限 → 413，不进堆")
    void should_reject413_when_bodyTooLarge() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null,
                new byte[OpenApiAuthFilter.MAX_BODY_BYTES + 1]);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("签名不符 → 401，而且 nonce 不登记（未授权请求不能占坑）")
    void should_reject401WithoutNonceRegistration_when_signatureInvalid() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        request.removeHeader(OpenApiSignature.HEADER_SIGNATURE);
        request.addHeader(OpenApiSignature.HEADER_SIGNATURE,
                OpenApiSignature.sign("wrong-secret", canonicalOf(request)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("签名"));
        verify(cacheHelper, never()).setIfAbsent(anyString(), anyString(), anyLong(),
                org.mockito.ArgumentMatchers.<TimeUnit>any());
    }

    @Test
    @DisplayName("只签路径不签查询串：改查询串即换签名")
    void should_reject401_when_queryTampered() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openapi/callback/order");
        request.setQueryString("page=1");
        request.setContent(BODY);
        String timestamp = currentEpoch();
        // 第三方漏签查询串：规范化串里没有 ?page=1
        String canonical = OpenApiSignature.canonicalString("POST", "/api/openapi/callback/order",
                timestamp, "n-1", BODY);
        addHeaders(request, timestamp, "n-1", OpenApiSignature.sign(SECRET, canonical));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("签名"));
    }

    @Test
    @DisplayName("nonce 在重放窗口内重复 → 401")
    void should_reject401_when_nonceReplayed() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.FALSE);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("nonce"));
    }

    @Test
    @DisplayName("nonce 存储不可用 → 503 并说「防护不可用」：判据不能悄悄失效，也不该谎称对方在重放")
    void should_reject503_when_nonceStoreUnavailable() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("redis down"));

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("Nonce store unavailable"));
        assertFalse(response.getContentAsString().contains("Replayed"),
                "自家 Redis 挂了不能说成调用方在重放");
    }

    @Test
    @DisplayName("超过应用配额 → 429 并带 Retry-After")
    void should_reject429_when_rateLimited() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        app.setRateLimit(2);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(3L);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("限流计数不可用 → 放行并告警（可用性护栏不是授权判据）")
    void should_failOpen_when_rateCounterUnavailable() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenThrow(new IllegalStateException("redis down"));

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("白名单是精确清单：配 10.0.0.15 不能顺手放行 10.0.0.1")
    void should_reject403_when_ipOnlyMatchesAsSubstring() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        app.setIpWhitelist("10.0.0.15");
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
        when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("白名单"));
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("放行时把 appId/tenantId 交给下游，且下游读到的是同一份请求体")
    void should_passThroughWithIdentity_when_everythingChecksOut() throws Exception {
        OpenApiApp app = app(42L, SECRET);
        app.setTenantId(7L);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
        when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertNotNull(downstream);
        // 输入流已被验签读过，下游仍能完整读出同一份字节
        assertArrayEquals(BODY, StreamUtils.copyToByteArray(downstream.getInputStream()));
        assertEquals(Long.valueOf(42L), request.getAttribute("appId"));
        assertEquals(Long.valueOf(7L), request.getAttribute("tenantId"));
    }

    @Test
    @DisplayName("非回调路径不进验签链")
    void should_skipFilter_when_pathIsNotCallback() {
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/openapi/apps")));
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/openapi/callback/order")));
    }

    @Test
    @DisplayName("前置一层可信代理：取 XFF 右数第二个，最左值是客户端自写的")
    void should_useSecondFromRight_when_oneTrustedProxyHop() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        app.setIpWhitelist("10.0.0.1");
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
        when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);
        ReflectionTestUtils.setField(filter, "trustedProxyHops", 1);

        MockHttpServletRequest legit = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        legit.setRemoteAddr("10.0.0.9");
        legit.addHeader("X-Forwarded-For", "6.6.6.6, 10.0.0.1");
        MockHttpServletResponse passResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(legit, passResponse, chain);
        assertEquals(200, passResponse.getStatus());
        assertNotNull(chain.getRequest());

        // 同一份合法签名，把 XFF 换成"自己声明的来源"就换不到放行
        MockHttpServletRequest forged = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        forged.setRemoteAddr("10.0.0.9");
        forged.addHeader("X-Forwarded-For", "10.0.0.1, 6.6.6.6");
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();
        filter.doFilter(forged, forgedResponse, new MockFilterChain());
        assertEquals(403, forgedResponse.getStatus());
    }

    @Test
    @DisplayName("默认不信任何代理：XFF 随便写，判据仍是 TCP 对端")
    void should_ignoreForwardedFor_when_noTrustedProxy() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        app.setIpWhitelist("10.0.0.1");
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
        when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        request.setRemoteAddr("6.6.6.6");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    // ---------- 拒绝审计：每一道 reject 都要入 openapi_log ----------

    @Test
    @DisplayName("缺签名头被拒：入账，路径/方法/状态码齐，归属留空（连 AppKey 都没查到）")
    void should_auditRefusal_when_authenticationHeadersMissing() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.MISSING_HEADERS);
        assertNull(refusal.getClientId(), "钥匙都没验过就写 clientId 等于让外部自称归属");
        assertNull(refusal.getAppName());
    }

    @Test
    @DisplayName("时间戳过期被拒：入账；AppKey 头带着也不能认领归属（判据在查库之前）")
    void should_auditRefusal_when_timestampExpired() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.EXPIRED_TIMESTAMP);
        assertEquals(Integer.valueOf(401), refusal.getResponseCode());
        assertNull(refusal.getClientId());
        assertNull(refusal.getAppName());
    }

    @Test
    @DisplayName("AppKey 不存在/已停用被拒：入账，没有可认领的身份")
    void should_auditRefusal_when_appKeyUnknown() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.UNKNOWN_APP_KEY);
        assertEquals(Integer.valueOf(401), refusal.getResponseCode());
        assertNull(refusal.getClientId());
        assertNull(refusal.getAppName());
    }

    @Test
    @DisplayName("请求体超限被拒：应用记录虽已查到，但密钥未验签 ⇒ 归属仍留空")
    void should_auditRefusalWithoutIdentity_when_bodyTooLarge() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.BODY_TOO_LARGE);
        assertEquals(Integer.valueOf(413), refusal.getResponseCode());
        assertNull(refusal.getClientId(), "验签之前不认领归属");
        assertNull(refusal.getAppName());
    }

    @Test
    @DisplayName("签名不符被拒：入账 401，且绝不因为查到应用就写下归属")
    void should_auditRefusalWithoutIdentity_when_signatureInvalid() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.INVALID_SIGNATURE);
        assertEquals(Integer.valueOf(401), refusal.getResponseCode());
        assertNull(refusal.getClientId(), "签名没过 = 对方没证明持有 appSecret");
        assertNull(refusal.getAppName());
    }

    @Test
    @DisplayName("nonce 重放被拒：验签已过 ⇒ 可以认领应用归属")
    void should_auditRefusalWithIdentity_when_nonceReplayed() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.REPLAYED_NONCE);
        assertEquals(Integer.valueOf(401), refusal.getResponseCode());
        assertEquals(Long.valueOf(7L), refusal.getClientId());
        assertEquals("回调接入方", refusal.getAppName());
    }

    @Test
    @DisplayName("超配额被拒：429 也入账（限流写在 isRateLimited 里，审计同样要覆盖）")
    void should_auditRefusalWithIdentity_when_rateLimited() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.RATE_LIMITED);
        assertEquals(Integer.valueOf(429), refusal.getResponseCode());
        assertEquals(Long.valueOf(7L), refusal.getClientId());
        assertEquals("回调接入方", refusal.getAppName());
    }

    @Test
    @DisplayName("来源 IP 不在白名单被拒：验签已过 ⇒ 入账 403 并认领归属")
    void should_auditRefusalWithIdentity_when_ipNotWhitelisted() throws Exception {
        OpenApiLog refusal = auditedRefusal(Refusal.IP_NOT_WHITELISTED);
        assertEquals(Integer.valueOf(403), refusal.getResponseCode());
        assertEquals(Long.valueOf(7L), refusal.getClientId());
        assertEquals("回调接入方", refusal.getAppName());
        assertEquals("10.0.0.1", refusal.getIp(), "白名单判据与审计 ip 必须同一个口径");
    }

    @Test
    @DisplayName("nonce 存储不可用的 fail-closed 拒绝同样入账：运维页看得见「防重放判据挂了」")
    void should_auditRefusal_when_nonceStoreUnavailable() throws Exception {
        OpenApiApp app = appWithIdentity();
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("redis down"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(signedRequest(app, CALLBACK_URI, null, BODY), response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("Nonce store unavailable"));
        assertNull(chain.getRequest(), "fail-closed：nonce 判据不可用不能放行");
        OpenApiLog refusal = capturedRefusalLog();
        assertEquals(Integer.valueOf(503), refusal.getResponseCode());
        assertEquals(CALLBACK_URI, refusal.getApiPath());
        assertEquals(Long.valueOf(7L), refusal.getClientId(), "验签已过，归属可认领");
        assertNull(refusal.getRequestParams());
    }

    @Test
    @DisplayName("审计 ip 用过滤器自己的 resolveClientIp：hops=0 时 XFF 写什么都抢不走")
    void should_recordResolvedIp_when_forwardedForSpoofed() throws Exception {
        // Given：默认 trusted-proxy-hops=0，调用方在 XFF 最左塞一个"看起来像内网"的地址
        MockHttpServletRequest request = callbackRequest();
        request.removeHeader(OpenApiSignature.HEADER_APP_KEY);
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 192.168.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals("10.0.0.9", capturedRefusalLog(0).getIp());

        // When：确实前置一层可信代理 ⇒ 取 XFF 从右往左第 1 项，最左的 6.6.6.6 仍不进审计
        ReflectionTestUtils.setField(filter, "trustedProxyHops", 1);
        MockHttpServletRequest behindProxy = callbackRequest();
        behindProxy.removeHeader(OpenApiSignature.HEADER_APP_KEY);
        behindProxy.setRemoteAddr("10.0.0.9");
        behindProxy.addHeader("X-Forwarded-For", "6.6.6.6, 10.0.0.7");
        filter.doFilter(behindProxy, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("10.0.0.7", capturedRefusalLog(1).getIp());
    }

    @Test
    @DisplayName("放行也写审计行：只记拒绝会让成功的一天在运维页上根本不存在")
    void should_auditAcceptedCall_when_requestAccepted() throws Exception {
        OpenApiApp app = appWithIdentity();
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        allowNonce();
        when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
        when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(signedRequest(app, CALLBACK_URI, null, BODY), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());

        ArgumentCaptor<OpenApiLog> captor = ArgumentCaptor.forClass(OpenApiLog.class);
        verify(apiLogService).saveLog(captor.capture());
        OpenApiLog accepted = captor.getValue();
        assertEquals(CALLBACK_URI, accepted.getApiPath());
        assertEquals(Integer.valueOf(200), accepted.getResponseCode(), "记的是业务真正回出去的状态码");
        assertEquals(Long.valueOf(7L), accepted.getClientId(), "验签已过 ⇒ 归属可以认领");
        assertNull(accepted.getRequestParams(), "回调体不进日志列");
    }

    @Test
    @DisplayName("审计写失败不改变对外响应：八道拒绝的状态码与响应体逐字不变")
    void should_keepEveryRefusalResponse_when_auditWriteFails() throws Exception {
        for (Refusal refusal : Refusal.values()) {
            MockHttpServletResponse response = fireRefusal(refusal, true);

            assertEquals(refusal.status, response.getStatus(), refusal.name());
            assertEquals(refusal.body(), response.getContentAsString(),
                    refusal.name() + "：响应体只写一次，审计异常不得改写或重复它");
        }
        // 每道拒绝都确实尝试过写入（失败被过滤器吞掉），一次都没漏
        verify(apiLogService, times(Refusal.values().length)).saveLog(any(OpenApiLog.class));
    }

    // ---------- helpers ----------

    /**
     * 拒绝分支的期望口径：状态码、响应体（与过滤器逐字对齐）、以及"这道关是否已经验过密钥"。
     * 身份可认领的三道（重放、限流、白名单）都在 {@code OpenApiSignature.matches} 之后。
     */
    private enum Refusal {
        MISSING_HEADERS(401, "缺少 X-App-Key / X-Timestamp / X-Nonce / X-Signature",
                "Missing authentication headers", false),
        EXPIRED_TIMESTAMP(401, "时间戳超出允许偏差", "Request expired", false),
        UNKNOWN_APP_KEY(401, "AppKey 不存在或已停用", "Invalid AppKey", false),
        BODY_TOO_LARGE(413, "请求体超过签名上限", "Request body too large", false),
        INVALID_SIGNATURE(401, "签名校验未通过", "Invalid Signature", false),
        REPLAYED_NONCE(401, "nonce 在重放窗口内重复出现", "Replayed request", true),
        RATE_LIMITED(429, "超过应用配额 2 次/分钟", "Rate limit exceeded", true),
        IP_NOT_WHITELISTED(403, "来源 IP 不在白名单", "IP not in whitelist", true);

        final int status;
        final String message;
        final String error;
        final boolean identityVerified;

        Refusal(int status, String message, String error, boolean identityVerified) {
            this.status = status;
            this.message = message;
            this.error = error;
            this.identityVerified = identityVerified;
        }

        String body() {
            return "{\"code\":" + status + ",\"message\":\"" + message + "\",\"error\":\"" + error + "\"}";
        }
    }

    /** 跑一次该分支的拒绝（审计写入照常成功），返回响应。 */
    private MockHttpServletResponse fireRefusal(Refusal refusal) throws Exception {
        return fireRefusal(refusal, false);
    }

    /** 造一次拒绝：备好该分支的桩与请求，跑过滤器；auditFails=true 时让 saveLog 抛异常。 */
    private MockHttpServletResponse fireRefusal(Refusal refusal, boolean auditFails) throws Exception {
        if (auditFails) {
            doThrow(new IllegalStateException("openapi_log 不可写"))
                    .when(apiLogService).saveLog(any(OpenApiLog.class));
        }
        OpenApiApp app = appWithIdentity();
        MockHttpServletRequest request = callbackRequest();
        switch (refusal) {
            case MISSING_HEADERS:
                request.removeHeader(OpenApiSignature.HEADER_SIGNATURE);
                break;
            case EXPIRED_TIMESTAMP:
                request.removeHeader(OpenApiSignature.HEADER_TIMESTAMP);
                request.addHeader(OpenApiSignature.HEADER_TIMESTAMP, String.valueOf(
                        System.currentTimeMillis() / 1000L - OpenApiSignature.SKEW_SECONDS - 5));
                break;
            case UNKNOWN_APP_KEY:
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(null);
                break;
            case BODY_TOO_LARGE:
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
                request = signedRequest(app, CALLBACK_URI, null,
                        new byte[OpenApiAuthFilter.MAX_BODY_BYTES + 1]);
                break;
            case INVALID_SIGNATURE:
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
                request = signedRequest(app, CALLBACK_URI, null, BODY);
                request.removeHeader(OpenApiSignature.HEADER_SIGNATURE);
                request.addHeader(OpenApiSignature.HEADER_SIGNATURE,
                        OpenApiSignature.sign("wrong-secret", canonicalOf(request)));
                break;
            case REPLAYED_NONCE:
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
                when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                        .thenReturn(Boolean.FALSE);
                request = signedRequest(app, CALLBACK_URI, null, BODY);
                break;
            case RATE_LIMITED:
                app.setRateLimit(2);
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
                allowNonce();
                when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(3L);
                request = signedRequest(app, CALLBACK_URI, null, BODY);
                break;
            case IP_NOT_WHITELISTED:
                app.setIpWhitelist("10.0.0.15");
                when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
                allowNonce();
                when(cacheHelper.increment(anyString(), eq(1L))).thenReturn(1L);
                when(cacheHelper.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(Boolean.TRUE);
                request = signedRequest(app, CALLBACK_URI, null, BODY);
                request.setRemoteAddr("10.0.0.1");
                break;
            default:
                throw new IllegalStateException("未接线的拒绝分支: " + refusal);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** 拒绝分支的共通审计断言：列齐、状态码与响应一致、且不落任何敏感内容；返回日志行供身份断言。 */
    private OpenApiLog auditedRefusal(Refusal refusal) throws Exception {
        MockHttpServletResponse response = fireRefusal(refusal);

        // (d) 审计不改变对外响应：状态码与响应体逐字等于没有审计时的形状
        assertEquals(refusal.status, response.getStatus());
        assertEquals(refusal.body(), response.getContentAsString());

        OpenApiLog row = capturedRefusalLog();
        assertEquals(CALLBACK_URI, row.getApiPath());
        assertEquals("POST", row.getMethod());
        assertEquals(Integer.valueOf(refusal.status), row.getResponseCode());
        assertNotNull(row.getIp());
        assertNotNull(row.getCreateTime());
        assertNotNull(row.getExecutionTime());
        assertTrue(row.getExecutionTime() >= 0L);

        // (c) 请求体/签名/nonce/密钥一个都不进列
        assertNull(row.getRequestParams(), "拒绝的调用不得把请求体抄进日志");
        String persisted = row.toString();
        assertFalse(persisted.contains(SECRET), "appSecret 不得出现在审计行任何一列");
        assertFalse(persisted.contains("order.created"), "请求体不得出现在审计行任何一列");

        // (b) 身份只在密钥验过之后认领
        if (refusal.identityVerified) {
            assertEquals(Long.valueOf(7L), row.getClientId(), refusal.name() + "：验签已过，应当认领归属");
            assertEquals("回调接入方", row.getAppName());
        } else {
            assertNull(row.getClientId(), refusal.name() + "：归属未经校验，写入等于让外部自称");
            assertNull(row.getAppName());
        }
        return row;
    }

    private OpenApiLog capturedRefusalLog() {
        ArgumentCaptor<OpenApiLog> captor = ArgumentCaptor.forClass(OpenApiLog.class);
        verify(apiLogService).saveLog(captor.capture());
        return captor.getValue();
    }

    /** 同一次测试里连打多道拒绝时用：按调用顺序拿到全部审计行。 */
    private OpenApiLog capturedRefusalLog(int index) {
        ArgumentCaptor<OpenApiLog> captor = ArgumentCaptor.forClass(OpenApiLog.class);
        verify(apiLogService, times(index + 1)).saveLog(captor.capture());
        List<OpenApiLog> rows = captor.getAllValues();
        return rows.get(index);
    }

    private static OpenApiApp appWithIdentity() {
        OpenApiApp app = app(7L, SECRET);
        app.setAppName("回调接入方");
        return app;
    }

    private MockHttpServletRequest requestWith(String timestamp, String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openapi/callback/order");
        request.setContent(BODY);
        addHeaders(request, timestamp, nonce, "deadbeef");
        return request;
    }

    private void allowNonce() {
        when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.TRUE);
    }

    private MockHttpServletRequest callbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openapi/callback/order");
        request.setContent(BODY);
        request.addHeader(OpenApiSignature.HEADER_APP_KEY, APP_KEY);
        request.addHeader(OpenApiSignature.HEADER_TIMESTAMP, currentEpoch());
        request.addHeader(OpenApiSignature.HEADER_NONCE, "n-1");
        request.addHeader(OpenApiSignature.HEADER_SIGNATURE, "deadbeef");
        return request;
    }

    private MockHttpServletRequest signedRequest(OpenApiApp app, String uri, String query, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (query != null) {
            request.setQueryString(query);
        }
        request.setContent(body);
        String timestamp = currentEpoch();
        addHeaders(request, timestamp, "n-1",
                OpenApiSignature.sign(app.getAppSecret(), OpenApiSignature.canonicalString(
                        "POST", OpenApiSignature.pathWithQuery(uri, query), timestamp, "n-1", body)));
        return request;
    }

    private static void addHeaders(MockHttpServletRequest request, String timestamp, String nonce,
                                   String signature) {
        request.addHeader(OpenApiSignature.HEADER_APP_KEY, APP_KEY);
        request.addHeader(OpenApiSignature.HEADER_TIMESTAMP, timestamp);
        request.addHeader(OpenApiSignature.HEADER_NONCE, nonce);
        request.addHeader(OpenApiSignature.HEADER_SIGNATURE, signature);
    }

    private String canonicalOf(MockHttpServletRequest request) {
        return OpenApiSignature.canonicalString(request.getMethod(),
                OpenApiSignature.pathWithQuery(request.getRequestURI(), request.getQueryString()),
                request.getHeader(OpenApiSignature.HEADER_TIMESTAMP),
                request.getHeader(OpenApiSignature.HEADER_NONCE),
                request.getContentAsByteArray());
    }

    private static String currentEpoch() {
        return String.valueOf(System.currentTimeMillis() / 1000L);
    }

    private static OpenApiApp app(Long id, String secret) {
        return OpenApiApp.builder()
                .id(id).appKey(APP_KEY).appSecret(secret).status(1).rateLimit(60).build();
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<OpenApiApp> anyWrapper() {
        return org.mockito.ArgumentMatchers.<Wrapper<OpenApiApp>>any();
    }
}
