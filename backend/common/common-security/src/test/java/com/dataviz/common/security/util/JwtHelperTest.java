package com.dataviz.common.security.util;

import com.dataviz.common.security.model.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具类。签名与验签共用一个进程内可变的静态密钥，所以这里既测算法口径（篡改必拒、换密钥必拒），
 * 也测「异常口径」：过期/无效必须落在 ExpiredJwtException / JwtException 上，
 * 而 null/空串走的是 IllegalArgumentException —— 这条不是 JwtException，上层 catch 不住。
 * 测试统一用自己的固定密钥，并用手工构造的 token，断言的是行为而不是生成器的自洽。
 */
class JwtHelperTest {

    private static final String TEST_SECRET = "unit-test-secret-key-0123456789-abcdefghijklmnopqrst-uvwxyz0123456789";
    private static final String OTHER_SECRET = "another-unit-test-secret-key-9876543210-zyxwvutsrqponmlkjihg-0123456789abcd";

    private String originalSecret;

    @BeforeEach
    void useFixedTestSecret() throws Exception {
        originalSecret = readSecret();
        writeSecret(TEST_SECRET);
    }

    @AfterEach
    void restoreProductionSecret() throws Exception {
        writeSecret(originalSecret);
    }

    @Test
    @DisplayName("签名→解析往返：subject 与四个自定义 claim 全部原样取回")
    void should_returnSameClaims_when_signThenParse() {
        // Given
        String token = JwtHelper.createToken(alice(), 600_000L);

        // When
        Claims claims = JwtHelper.parseToken(token);

        // Then
        assertEquals("alice", claims.getSubject());
        assertEquals(Long.valueOf(7), claims.get("userId", Long.class));
        assertEquals("T1", claims.get("tenantId", String.class));
        assertEquals(Long.valueOf(3), claims.get("deptId", Long.class));
        assertEquals("alice", claims.get("username", String.class));
    }

    @Test
    @DisplayName("三个取值器分别对应 userId / subject / tenantId")
    void should_exposeAccessors_when_tokenIsValid() {
        // Given
        String token = JwtHelper.createToken(alice(), 600_000L);

        // When/Then
        assertEquals(Long.valueOf(7), JwtHelper.getUserId(token));
        assertEquals("alice", JwtHelper.getUsername(token));
        assertEquals("T1", JwtHelper.getTenantId(token));
    }

    @Test
    @DisplayName("缺 tenantId 声明时取值为 null，不抛异常也不给默认租户")
    void should_returnNull_when_claimAbsent() {
        // Given：手工构造只带 sub 的 token
        String token = signedWithTestSecret("{\"sub\":\"bob\"}", future(60_000L));

        // When/Then
        assertNull(JwtHelper.getTenantId(token));
        assertNull(JwtHelper.getUserId(token));
        assertEquals("bob", JwtHelper.getUsername(token));
    }

    @Test
    @DisplayName("有效 token：validate=true、isTokenExpired=false、剩余秒数落在区间内")
    void should_reportHealthy_when_tokenFresh() {
        // Given
        String token = JwtHelper.createToken(alice(), 3_600_000L);

        // When/Then
        assertTrue(JwtHelper.validateToken(token));
        assertFalse(JwtHelper.isTokenExpired(token));
        long remaining = JwtHelper.getRemainingExpiration(token);
        assertTrue(remaining >= 3590 && remaining <= 3600, "remaining=" + remaining);
    }

    @Test
    @DisplayName("过期 token 抛 ExpiredJwtException，且 validate=false / isTokenExpired=true / 剩余 0")
    void should_throwExpired_when_tokenPastExpiry() {
        // Given
        String token = JwtHelper.createToken(alice(), -60_000L);

        // When/Then
        ExpiredJwtException ex = assertThrows(ExpiredJwtException.class, () -> JwtHelper.parseToken(token));
        assertTrue(ex.getMessage().contains("expired"), ex.getMessage());
        assertFalse(JwtHelper.validateToken(token));
        assertTrue(JwtHelper.isTokenExpired(token));
        assertEquals(0, JwtHelper.getRemainingExpiration(token));
    }

    @Test
    @DisplayName("篡改 payload 段（换 subject）后签名校验必拒")
    void should_reject_when_payloadTampered() {
        // Given
        String token = JwtHelper.createToken(alice(), 600_000L);
        String[] parts = token.split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"admin\",\"userId\":999,\"tenantId\":\"T9\"}");
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        // When
        JwtException ex = assertThrows(JwtException.class, () -> JwtHelper.parseToken(tampered));

        // Then
        assertEquals(SignatureException.class, ex.getClass());
        assertFalse(JwtHelper.validateToken(tampered));
        assertTrue(JwtHelper.isTokenExpired(tampered));
    }

    @Test
    @DisplayName("换密钥签发的同内容 token 必拒：签名唯一依赖密钥")
    void should_reject_when_signedWithOtherSecret() {
        // Given
        String foreign = Jwts.builder()
                .subject("mallory")
                .claim("userId", 1L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        // When/Then
        assertEquals(SignatureException.class,
                assertThrows(JwtException.class, () -> JwtHelper.parseToken(foreign)).getClass());
    }

    @Test
    @DisplayName("密钥按原始字节参与 HMAC（类里的 BASE64 套 BASE64 解是空操作）：手工构造的签名必须被接受")
    void should_acceptRawBytesKey_when_tokenBuiltOutsideHelper() {
        // Given
        String token = signedWithTestSecret("{\"sub\":\"carol\",\"tenantId\":\"T3\"}", future(60_000L));

        // When/Then
        assertEquals("carol", JwtHelper.getUsername(token));
        assertEquals("T3", JwtHelper.getTenantId(token));
    }

    @Test
    @DisplayName("密钥是进程内可变静态量：换密钥后旧 token 立即全部失效（无法平滑轮换）")
    void should_invalidateOldTokens_when_secretRotated() {
        // Given
        String token = JwtHelper.createToken(alice(), 600_000L);

        // When
        writeSecret(OTHER_SECRET);

        // Then
        assertEquals(SignatureException.class,
                assertThrows(JwtException.class, () -> JwtHelper.parseToken(token)).getClass());
    }

    @Test
    @DisplayName("刷新后的 token 换了新过期时间，身份四要素保持不变")
    void should_keepIdentityAndExtendExpiry_when_refreshed() {
        // Given
        String old = JwtHelper.createToken(alice(), 60_000L);
        long oldRemaining = JwtHelper.getRemainingExpiration(old);

        // When
        String refreshed = JwtHelper.refreshToken(old);

        // Then
        assertEquals("alice", JwtHelper.getUsername(refreshed));
        assertEquals(Long.valueOf(7), JwtHelper.getUserId(refreshed));
        assertEquals("T1", JwtHelper.getTenantId(refreshed));
        assertTrue(JwtHelper.getRemainingExpiration(refreshed) > oldRemaining, "refreshed remaining not extended");
    }

    @Test
    @DisplayName("刷新已过期的 token 直接抛 ExpiredJwtException：救不回过期会话")
    void should_throwExpired_when_refreshExpiredToken() {
        // Given
        String expired = JwtHelper.createToken(alice(), -60_000L);

        // When/Then
        assertThrows(ExpiredJwtException.class, () -> JwtHelper.refreshToken(expired));
    }

    @Test
    @DisplayName("刷新会把任何自定义有效期改写成 24 小时默认值：短效 token 会被静默拉长")
    void should_resetToDefaultExpiry_when_refreshLongOrShortLivedToken() {
        // Given
        String fifteenMinutes = JwtHelper.createToken(alice(), 900_000L);

        // When
        String refreshed = JwtHelper.refreshToken(fifteenMinutes);

        // Then
        long remaining = JwtHelper.getRemainingExpiration(refreshed);
        assertTrue(remaining >= 86300 && remaining <= 86400, "remaining=" + remaining);
    }

    @Test
    @DisplayName("默认签发按 24 小时给有效期")
    void should_useTwentyFourHours_when_expirationNotGiven() {
        // Given/When
        String token = JwtHelper.createToken(alice());

        // Then
        long remaining = JwtHelper.getRemainingExpiration(token);
        assertTrue(remaining >= 86390 && remaining <= 86400, "remaining=" + remaining);
    }

    @Test
    @DisplayName("null / 空串 / 非 JWT 串都不是 JwtException 口径：前两者抛 IllegalArgumentException，上层 catch(JwtException) 兜不住")
    void should_throwIllegalArgument_when_tokenNullOrBlank() {
        // Given/When/Then
        IllegalArgumentException nullCase = assertThrows(IllegalArgumentException.class, () -> JwtHelper.parseToken(null));
        assertTrue(nullCase.getMessage().contains("null or empty"), nullCase.getMessage());
        assertThrows(IllegalArgumentException.class, () -> JwtHelper.parseToken(""));
        assertThrows(IllegalArgumentException.class, () -> JwtHelper.validateToken("  "));
        // 结构非法（无分隔符）走 MalformedJwtException，属于 JwtException
        assertEquals(io.jsonwebtoken.MalformedJwtException.class,
                assertThrows(JwtException.class, () -> JwtHelper.parseToken("not-a-jwt")).getClass());
    }

    @Test
    @DisplayName("结构非法的 token：validate=false、isTokenExpired=true、剩余 0，三种口径互相一致")
    void should_reportInvalidConsistently_when_tokenMalformed() {
        // Given
        String junk = "aaa.bbb.ccc";

        // When/Then
        assertFalse(JwtHelper.validateToken(junk));
        assertTrue(JwtHelper.isTokenExpired(junk));
        assertEquals(0, JwtHelper.getRemainingExpiration(junk));
    }

    @Test
    @DisplayName("JwtHelper 是纯静态工具：不可实例化")
    void should_beUtility_when_classInspected() throws Exception {
        // Given
        java.lang.reflect.Constructor<JwtHelper> ctor = JwtHelper.class.getDeclaredConstructor();

        // When
        ctor.setAccessible(true);
        Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> ctor.newInstance()).getCause();

        // Then
        assertEquals(IllegalStateException.class, cause.getClass());
        assertEquals("Utility class", cause.getMessage());
    }

    // ---------- helpers ----------

    private static LoginUser alice() {
        return LoginUser.builder()
                .userId(7L).username("alice").nickname("Alice").tenantId("T1").deptId(3L)
                .roles(java.util.Collections.singleton("user"))
                .permissions(java.util.Collections.singleton("dashboard:view"))
                .build();
    }

    private static String signedWithTestSecret(String payloadJson, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .claims(JsonSupport.parse(payloadJson))
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    private static Date future(long millis) {
        return new Date(System.currentTimeMillis() + millis);
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String readSecret() throws Exception {
        Field field = JwtHelper.class.getDeclaredField("secretKey");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static void writeSecret(String value) {
        try {
            Field field = JwtHelper.class.getDeclaredField("secretKey");
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 把 payload JSON 变成 claim Map，避免为了拼 claims 再引一次 Jackson 类型 */
    private static final class JsonSupport {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private static java.util.Map<String, Object> parse(String json) {
            try {
                return MAPPER.readValue(json,
                        MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class,
                                String.class, Object.class));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
