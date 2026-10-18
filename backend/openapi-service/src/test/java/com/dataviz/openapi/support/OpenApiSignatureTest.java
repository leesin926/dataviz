package com.dataviz.openapi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对外回调的签名形状。
 *
 * <p>这里的判据不是"HMAC 算得对"——那是 JDK 的事——而是"服务端期望的规范化串，
 * 和第三方按文档拼出来的串是同一份字节"。所以关键用例是一条用 Node crypto 独立算出来的
 * 已知向量：如果拼串顺序、分隔符、大小写或体哈希口径变了，这条会先红。</p>
 */
class OpenApiSignatureTest {

    private static final String SECRET = "test-secret";
    private static final byte[] BODY = "{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("规范化串 - 五段以换行连接且末尾无换行")
    void should_joinFiveSegmentsWithoutTrailingNewline_when_canonical() {
        String canonical = OpenApiSignature.canonicalString(
                "post", "/api/openapi/callback/order?tag=a", "1700000000", "n-1", BODY);

        String[] segments = canonical.split("\n");
        assertEquals(5, segments.length);
        assertEquals("POST", segments[0]);
        assertEquals("/api/openapi/callback/order?tag=a", segments[1]);
        assertEquals("1700000000", segments[2]);
        assertEquals("n-1", segments[3]);
        assertFalse(canonical.endsWith("\n"));
    }

    @Test
    @DisplayName("签名 - 命中独立实现算出的已知向量")
    void should_matchIndependentVector_when_signKnownInput() {
        String canonical = OpenApiSignature.canonicalString(
                "POST", "/api/openapi/callback/order?tag=a", "1700000000", "n-1", BODY);

        // 期望值由 Node crypto 对同一份字节算出：HMAC-SHA256(secret, canonical)
        assertEquals("5564daaa44d708aaa82bde03bbe11ea470bdb591ac27cd48921a298080601eb6",
                OpenApiSignature.sign(SECRET, canonical));
    }

    @Test
    @DisplayName("签名 - 同一输入重复计算结果一致")
    void should_beDeterministic_when_signTwice() {
        String canonical = OpenApiSignature.canonicalString(
                "POST", "/x", "1", "n", BODY);
        String first = OpenApiSignature.sign(SECRET, canonical);

        assertEquals(64, first.length());
        assertEquals(first.toLowerCase(), first);
        assertEquals(first, OpenApiSignature.sign(SECRET, canonical));
    }

    @Test
    @DisplayName("空体与 null 体按同一口径处理")
    void should_hashEmptyBytes_when_bodyNull() {
        // SHA-256("") 的公开常量：e3b0c442...b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                OpenApiSignature.sha256Hex(null));
        assertEquals(OpenApiSignature.sha256Hex(new byte[0]), OpenApiSignature.sha256Hex(null));
    }

    @Test
    @DisplayName("请求体进签名：改一个字节就换签名")
    void should_changeSignature_when_bodyDiffers() {
        String canonical = OpenApiSignature.canonicalString("POST", "/x", "1", "n", BODY);
        String tampered = OpenApiSignature.canonicalString(
                "POST", "/x", "1", "n", "{\"k\":\"w\"}".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(canonical, tampered);
        assertFalse(OpenApiSignature.matches(SECRET, tampered, OpenApiSignature.sign(SECRET, canonical)));
    }

    @Test
    @DisplayName("方法/路径/时间戳/nonce 四段各自都进签名")
    void should_changeSignature_when_anyHeaderSegmentDiffers() {
        String base = OpenApiSignature.canonicalString("POST", "/x", "1", "n", BODY);

        assertNotEquals(base, OpenApiSignature.canonicalString("GET", "/x", "1", "n", BODY));
        assertNotEquals(base, OpenApiSignature.canonicalString("POST", "/y", "1", "n", BODY));
        assertNotEquals(base, OpenApiSignature.canonicalString("POST", "/x", "2", "n", BODY));
        assertNotEquals(base, OpenApiSignature.canonicalString("POST", "/x", "1", "m", BODY));
    }

    @Test
    @DisplayName("带查询串的路径才算完整 URI：GET 回调的参数藏在查询串里")
    void should_appendQuery_when_present() {
        assertEquals("/api/openapi/callback/list",
                OpenApiSignature.pathWithQuery("/api/openapi/callback/list", null));
        assertEquals("/api/openapi/callback/list",
                OpenApiSignature.pathWithQuery("/api/openapi/callback/list", ""));
        assertEquals("/api/openapi/callback/list?page=2",
                OpenApiSignature.pathWithQuery("/api/openapi/callback/list", "page=2"));
    }

    @Test
    @DisplayName("验签接受大小写与首尾空白，拒绝 null/错签/错密钥")
    void should_verifySignature_when_compareProvided() {
        String canonical = OpenApiSignature.canonicalString("POST", "/x", "1", "n", BODY);
        String signature = OpenApiSignature.sign(SECRET, canonical);

        assertTrue(OpenApiSignature.matches(SECRET, canonical, signature));
        assertTrue(OpenApiSignature.matches(SECRET, canonical, "  " + signature.toUpperCase() + "  "));
        assertFalse(OpenApiSignature.matches(SECRET, canonical, null));
        assertFalse(OpenApiSignature.matches(SECRET, canonical, signature.substring(0, 63) + "0"));
        assertFalse(OpenApiSignature.matches("other-secret", canonical, signature));
    }

    @Test
    @DisplayName("密钥与规范化串都按 UTF-8 取字节，不受平台默认字符集影响")
    void should_useUtf8_when_secretAndBodyContainChinese() {
        String canonical = OpenApiSignature.canonicalString(
                "POST", "/a", "1", "n", "正文".getBytes(StandardCharsets.UTF_8));

        // 期望值由 Node crypto 以 UTF-8 对 "密钥" 与同一段落算出
        assertEquals("1cb0811141bb0f9e44d1030f61923d15563f2338465c7526bdaad3407cd26997",
                OpenApiSignature.sign("密钥", canonical));
    }
}
