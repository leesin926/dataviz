package com.dataviz.common.security.interceptor;

import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.common.security.util.JwtHelper;
import com.dataviz.common.security.util.LoginSessionCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 认证拦截器。这里盯四件事：
 * ①拒绝时到底写了什么（401 状态 + 统一信封的 code/message），因为前端按 code 分支；
 * ②Redis 会话快照优先于 JWT 语法有效 —— token 没过期但人已登出必须拒；
 * ③滑动续期与 token 刷新的阈值边界（30 分钟）；
 * ④过期/无效两类 JwtException 的文案分别落到 TOKEN_EXPIRED / TOKEN_INVALID，
 *   但注意信封 code 恒为 401，具体错误码 1004/1005 被丢掉了（已作为观察项记录）。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String USERNAME = "alice";
    private static final String SESSION_KEY = LoginSessionCache.key(USERNAME);

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private CacheHelper cacheHelper;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(cacheHelper);
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ---------- 放行 ----------

    @Test
    @DisplayName("OPTIONS 预检直接放行，不查头也不碰 Redis")
    void should_passThrough_when_optionsPreflight() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/dashboard/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        boolean passed = interceptor.preHandle(request, response, new Object());

        // Then
        assertTrue(passed);
        assertEquals(200, response.getStatus());
        verifyNoInteractions(cacheHelper);
    }

    @Test
    @DisplayName("token 有效且会话快照在：放行并把快照放进 ThreadLocal")
    void should_passAndBindContext_when_tokenValidAndSessionCached() throws Exception {
        // Given
        LoginUser cached = cachedUser();
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cached);
        when(cacheHelper.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(7000L);

        // When
        boolean passed = interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)),
                new MockHttpServletResponse(), new Object());

        // Then
        assertTrue(passed);
        assertSame(cached, SecurityContextHolder.getLoginUser());
        assertEquals("alice", SecurityContextHolder.getUsername());
    }

    @Test
    @DisplayName("会话剩余不足 30 分钟时按 TTL_SECONDS 滑动续期")
    void should_extendSession_when_sessionRemainingBelowThirtyMinutes() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cachedUser());
        when(cacheHelper.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(1799L);

        // When
        interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)),
                new MockHttpServletResponse(), new Object());

        // Then
        verify(cacheHelper).expire(SESSION_KEY, LoginSessionCache.TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("会话剩余超过 30 分钟时不续期")
    void should_notExtendSession_when_remainingAboveThirtyMinutes() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cachedUser());
        when(cacheHelper.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(1801L);

        // When
        interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)),
                new MockHttpServletResponse(), new Object());

        // Then
        verify(cacheHelper, never()).expire(anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("会话没有 TTL（Redis 返回 -1/null）时不续期，但仍放行")
    void should_notExtendSession_when_expireReturnsNull() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cachedUser());
        when(cacheHelper.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(-1L);

        // When
        boolean passed = interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)),
                new MockHttpServletResponse(), new Object());

        // Then
        assertTrue(passed);
        verify(cacheHelper, never()).expire(anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("token 距过期不足 30 分钟时下发 New-Token 头，且新 token 是 24 小时口径")
    void should_issueNewTokenHeader_when_tokenCloseToExpiry() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cachedUser());
        String soonToExpire = JwtHelper.createToken(cachedUser(), 60_000L);

        // When
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean passed = interceptor.preHandle(requestWithToken(soonToExpire), response, new Object());

        // Then
        assertTrue(passed);
        String newToken = response.getHeader("New-Token");
        assertNotNull(newToken);
        assertEquals("alice", JwtHelper.getUsername(newToken));
        assertTrue(JwtHelper.getRemainingExpiration(newToken) > 86300, "new token should live ~24h");
    }

    @Test
    @DisplayName("token 还很新时不下发 New-Token 头")
    void should_notIssueNewToken_when_tokenFresh() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(cachedUser());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)), response, new Object());

        // Then
        assertNull(response.getHeader("New-Token"));
    }

    @Test
    @DisplayName("会话键只按用户名：同名跨租户时，甲租户的 token 会绑上乙租户的身份快照（不比对 tenantId）")
    void should_bindWhateverTenantTheCacheHolds_when_usernameCollidesAcrossTenants() throws Exception {
        // Given：token 属于租户 T-A 的 alice，而 login:user:alice 里是租户 T-B 的同名用户（用户名只在租户内唯一）
        LoginUser tenantAAlice = cachedUser();
        LoginUser tenantBAlice = LoginUser.builder()
                .userId(99L).username(USERNAME).tenantId("T-B")
                .roles(java.util.Collections.singleton("super_admin"))
                .permissions(java.util.Collections.singleton("system:user:list"))
                .build();
        when(cacheHelper.get(SESSION_KEY)).thenReturn(tenantBAlice);

        // When
        boolean passed = interceptor.preHandle(requestWithToken(JwtHelper.createToken(tenantAAlice, 3_600_000L)),
                new MockHttpServletResponse(), new Object());

        // Then：拦截器只按 subject 取快照，从不校验快照租户与 token 租户是否一致
        assertTrue(passed);
        assertSame(tenantBAlice, SecurityContextHolder.getLoginUser());
        assertEquals("T-B", SecurityContextHolder.getTenantId());
        assertEquals(Long.valueOf(99), SecurityContextHolder.getUserId());
    }

    // ---------- 拒绝：请求头形态 ----------

    @Test
    @DisplayName("完全没带 Authorization：401 + 信封 code=401 + 未认证文案")
    void should_write401_when_authorizationHeaderMissing() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request, response, new Object());

        // Then
        assertFalse(passed);
        assertEquals(401, response.getStatus());
        assertEnvelope(response, 401, "未认证或认证已过期");
        verifyNoInteractions(cacheHelper);
    }

    @Test
    @DisplayName("空值头 / 纯空白头同样按缺失处理")
    void should_write401_when_headerBlank() throws Exception {
        // Given/When/Then
        for (String header : new String[]{"", "   "}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
            request.addHeader("Authorization", header);
            MockHttpServletResponse response = utf8Response();

            assertFalse(interceptor.preHandle(request, response, new Object()), header);
            assertEquals(401, response.getStatus(), header);
            assertEnvelope(response, 401, "未认证或认证已过期");
        }
    }

    @Test
    @DisplayName("前缀大小写敏感：小写 bearer 被拒（与 RFC 6749 不一致，已记为观察项）")
    void should_write401_when_schemeIsLowerCase() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
        request.addHeader("Authorization",
                "bearer " + JwtHelper.createToken(cachedUser(), 3_600_000L));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request, response, new Object());

        // Then
        assertFalse(passed);
        assertEquals(401, response.getStatus());
        assertEnvelope(response, 401, "未认证或认证已过期");
    }

    @Test
    @DisplayName("另一种前缀（Token xxx）被拒")
    void should_write401_when_otherAuthScheme() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
        request.addHeader("Authorization", "Token " + JwtHelper.createToken(cachedUser(), 3_600_000L));
        MockHttpServletResponse response = utf8Response();

        // When/Then
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEnvelope(response, 401, "未认证或认证已过期");
    }

    @Test
    @DisplayName("只写 \"Bearer \"（空 token）时抛 IllegalArgumentException 逃出 preHandle：前端拿到 500 而不是 401")
    void should_leakIllegalArgument_when_bearerPrefixWithEmptyToken() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = utf8Response();

        // When
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        // Then：既没写 401 也没查会话
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
        assertTrue(ex.getMessage().contains("null or empty"), ex.getMessage());
        verifyNoInteractions(cacheHelper);
    }

    // ---------- 拒绝：token 与会话 ----------

    @Test
    @DisplayName("JWT 语法有效但 Redis 里没有会话（已登出/被盗用）→ 401 +「登录已过期，请重新登录」")
    void should_write401_when_sessionCacheMiss() throws Exception {
        // Given
        when(cacheHelper.get(SESSION_KEY)).thenReturn(null);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(requestWithToken(JwtHelper.createToken(cachedUser(), 3_600_000L)),
                response, new Object());

        // Then
        assertFalse(passed);
        assertEquals(401, response.getStatus());
        assertEnvelope(response, 401, "登录已过期，请重新登录");
        assertNull(SecurityContextHolder.getLoginUser());
    }

    @Test
    @DisplayName("token 已过期 → 401，文案是 TOKEN_EXPIRED 的「Token已过期」")
    void should_write401WithExpiredMessage_when_tokenExpired() throws Exception {
        // Given
        String expired = JwtHelper.createToken(cachedUser(), -60_000L);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(requestWithToken(expired), response, new Object());

        // Then
        assertFalse(passed);
        assertEquals(401, response.getStatus());
        // 具体错误码 1004 被丢弃，信封 code 恒为 401 —— 前端只能靠 message 区分
        assertEnvelope(response, 401, "Token已过期");
        verifyNoInteractions(cacheHelper);
    }

    @Test
    @DisplayName("token 被篡改 → 401，文案是 TOKEN_INVALID 的「Token无效」")
    void should_write401WithInvalidMessage_when_signatureTampered() throws Exception {
        // Given
        String[] parts = JwtHelper.createToken(cachedUser(), 3_600_000L).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(requestWithToken(parts[0] + "." + forgedPayload + "." + parts[2]),
                response, new Object());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 401, "Token无效");
    }

    @Test
    @DisplayName("完全不是 JWT 的串 → 401「Token无效」")
    void should_write401_when_tokenIsGarbage() throws Exception {
        // Given
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(requestWithToken("aaa.bbb.ccc"), response, new Object());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 401, "Token无效");
    }

    // ---------- afterCompletion ----------

    @Test
    @DisplayName("afterCompletion 清 ThreadLocal，避免线程复用串号")
    void should_clearContext_when_afterCompletion() {
        // Given
        SecurityContextHolder.setLoginUser(cachedUser());
        assertNotNull(SecurityContextHolder.getLoginUser());

        // When
        interceptor.afterCompletion(new MockHttpServletRequest("GET", "/dashboard/list"),
                new MockHttpServletResponse(), new Object(), null);

        // Then
        assertNull(SecurityContextHolder.getLoginUser());
        assertFalse(SecurityContextHolder.isAuthenticated());
        assertNull(SecurityContextHolder.getUserId());
    }

    @Test
    @DisplayName("放行后异常收尾也要清上下文")
    void should_clearContext_when_requestFinishedWithException() {
        // Given
        SecurityContextHolder.setLoginUser(cachedUser());

        // When
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object(), new IllegalStateException("boom"));

        // Then
        assertNull(SecurityContextHolder.getLoginUser());
    }

    // ---------- helpers ----------

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/list");
        request.addHeader(SecurityContextHolder.getHeaderToken(), "Bearer " + token);
        return request;
    }

    private LoginUser cachedUser() {
        return LoginUser.builder()
                .userId(7L).username(USERNAME).nickname("Alice").tenantId("T1").deptId(3L)
                .roles(java.util.Collections.singleton("user"))
                .permissions(java.util.Collections.singleton("dashboard:view"))
                .expireTime(System.currentTimeMillis() + 3_600_000L)
                .build();
    }

    /** MockHttpServletResponse 默认按 ISO-8859-1 编码 writer，先钉成 UTF-8 才能断言中文文案 */
    private MockHttpServletResponse utf8Response() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private String bodyOf(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private void assertEnvelope(MockHttpServletResponse response, int expectedCode, String expectedMessage)
            throws Exception {
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        Map<String, Object> envelope = mapper.readValue(bodyOf(response),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(expectedCode, envelope.get("code"));
        assertEquals(expectedMessage, envelope.get("message"));
        assertNull(envelope.get("data"));
    }
}
