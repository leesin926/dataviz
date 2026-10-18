package com.dataviz.openapi.filter;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import com.dataviz.openapi.support.OpenApiSignature;
import com.dataviz.openapi.test.MybatisPlusTableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回调验签链的分支覆盖。重点不在"能不能通过"，而在四件事：
 * 判据顺序（先验签后登记 nonce，否则未授权请求就能占坑挤掉合法请求）、
 * 故障口径（nonce 挂了必须拒、限流挂了可以放）、
 * 白名单是精确清单不是子串、
 * 客户端地址默认只信 TCP 对端。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAuthFilterTest {

    private static final String APP_KEY = "ak-unit-test";
    private static final String SECRET = "unit-test-secret";
    private static final byte[] BODY = "{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8);

    @Mock
    private OpenApiAppMapper openApiAppMapper;

    @Mock
    private CacheHelper cacheHelper;

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
    @DisplayName("nonce 存储不可用 → 拒（防重放判据不能悄悄失效）")
    void should_reject401_when_nonceStoreUnavailable() throws Exception {
        OpenApiApp app = app(1L, SECRET);
        when(openApiAppMapper.selectOne(anyWrapper())).thenReturn(app);
        when(cacheHelper.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("redis down"));

        MockHttpServletRequest request = signedRequest(app, "/api/openapi/callback/order", null, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("nonce"));
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

    // ---------- helpers ----------

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
