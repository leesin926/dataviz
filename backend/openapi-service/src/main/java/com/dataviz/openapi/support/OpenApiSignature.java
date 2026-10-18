package com.dataviz.openapi.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 对外回调的 HMAC-SHA256 签名算法。调用方与服务端必须逐字节一致，所以把拼串形状固定成一处：
 *
 * <pre>
 * method
 * requestURI?queryString
 * timestamp
 * nonce
 * hex(sha256(rawBody))
 * </pre>
 *
 * 五段以 \n 连接，末尾无换行。签名头是小写十六进制。
 */
public final class OpenApiSignature {

    public static final String HEADER_APP_KEY = "X-App-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    /** 允许的时钟偏差。超出即拒，配合 nonce 的 TTL 一起决定重放窗口。 */
    public static final long SKEW_SECONDS = 300L;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private OpenApiSignature() {
    }

    public static String canonicalString(String method, String pathWithQuery, String timestamp,
                                        String nonce, byte[] body) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(method.toUpperCase()).append('\n')
                .append(pathWithQuery).append('\n')
                .append(timestamp).append('\n')
                .append(nonce).append('\n')
                .append(sha256Hex(body));
        return sb.toString();
    }

    /** 带查询串的路径；GET/表单回调的参数就藏在查询串里，不能只签路径。 */
    public static String pathWithQuery(String requestUri, String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return requestUri;
        }
        return requestUri + "?" + queryString;
    }

    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bytes(secret), "HmacSHA256"));
            return toHex(mac.doFinal(bytes(canonical)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 自带 HmacSHA256；走到这里只能是运行时被裁剪过，宁可比验不通过也不要退化成不验签
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign openapi request", e);
        }
    }

    /** 定长比较：签名不匹配与长度不同都只返回 false，且耗时不泄漏期望值。 */
    public static boolean matches(String secret, String canonical, String providedSignature) {
        if (providedSignature == null) {
            return false;
        }
        byte[] expected = bytes(sign(secret, canonical));
        byte[] actual = bytes(providedSignature.trim().toLowerCase());
        return MessageDigest.isEqual(expected, actual);
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data == null ? new byte[0] : data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
