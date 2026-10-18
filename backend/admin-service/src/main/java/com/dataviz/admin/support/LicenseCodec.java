package com.dataviz.admin.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 许可证密钥的编解码：{@code DVIZ1.<base64url(payload)>.<base64url(hmac)>}。
 * <p>payload 是 6 段竖线分隔的定长文本（版本 | 客户 | 签发日 | 到期日 | 用户上限 | 节点上限），
 * 签名算在 <b>base64url 之后的 payload 段</b>上 —— 验签只需要把字符串原样喂给 HMAC，
 * 不需要重新序列化对象，也就不会因为在两处写了"差不多的"规范化规则而永远验不过。</p>
 *
 * <p>这里<b>没有</b>内置默认密钥：密钥为空时 {@link #verify} 直接拒绝。
 * 硬编码一个"演示密钥"等于给所有部署发了万能钥匙 —— 拿到代码的人可以自己签一张永久许可证。</p>
 */
public final class LicenseCodec {

    private static final String PREFIX = "DVIZ1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int SEGMENTS = 6;

    private LicenseCodec() {
        throw new IllegalStateException("Utility class");
    }

    /** 许可证承载的字段 */
    public static final class Payload {
        public final String edition;
        public final String issuedTo;
        public final LocalDate issuedAt;
        public final LocalDate expiresAt;
        public final int maxUsers;
        public final int maxNodes;

        Payload(String edition, String issuedTo, LocalDate issuedAt, LocalDate expiresAt, int maxUsers, int maxNodes) {
            this.edition = edition;
            this.issuedTo = issuedTo;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.maxUsers = maxUsers;
            this.maxNodes = maxNodes;
        }
    }

    /** 验签结果：要么拿到 payload，要么拿到一句能直接给用户看的原因 */
    public static final class Outcome {
        public final Payload payload;
        public final String error;

        private Outcome(Payload payload, String error) {
            this.payload = payload;
            this.error = error;
        }

        public boolean isValid() {
            return payload != null;
        }
    }

    public static String issue(String secret, String edition, String issuedTo, LocalDate issuedAt,
                               LocalDate expiresAt, int maxUsers, int maxNodes) {
        requireSecret(secret);
        String payload = join(edition, issuedTo, issuedAt, expiresAt, maxUsers, maxNodes);
        String encoded = encode(payload);
        return PREFIX + "." + encoded + "." + encode(sign(secret, encoded));
    }

    public static Outcome verify(String secret, String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return new Outcome(null, "许可证密钥为空");
        }
        if (secret == null || secret.trim().isEmpty()) {
            return new Outcome(null, "服务端未配置许可证签发密钥（platform.license.secret），无法验签");
        }
        String key = licenseKey.trim();
        String[] parts = key.split("\\.");
        if (parts.length != 3) {
            return new Outcome(null, "许可证密钥格式不对，应为 " + PREFIX + ".<载荷>.<签名>");
        }
        if (!PREFIX.equals(parts[0])) {
            return new Outcome(null, "许可证版本前缀不认识: " + parts[0]);
        }
        byte[] providedSignature;
        try {
            providedSignature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return new Outcome(null, "许可证签名段不是合法的 base64url");
        }
        byte[] expected = sign(secret, parts[1]);
        // 定长比较用 MessageDigest.isEqual：逐字节短路比较会让攻击者按响应时间差分猜签名
        if (!java.security.MessageDigest.isEqual(expected, providedSignature)) {
            return new Outcome(null, "许可证签名不匹配（密钥被改过，或不是本部署的签发密钥签的）");
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return new Outcome(null, "许可证载荷段不是合法的 base64url");
        }
        String[] segments = payload.split("\\|", -1);
        if (segments.length != SEGMENTS) {
            return new Outcome(null, "许可证载荷字段数不对，期望 " + SEGMENTS + " 段，实际 " + segments.length + " 段");
        }
        try {
            Payload parsed = new Payload(segments[0], segments[1],
                    LocalDate.parse(segments[2]), LocalDate.parse(segments[3]),
                    Integer.parseInt(segments[4]), Integer.parseInt(segments[5]));
            if (parsed.maxUsers <= 0 || parsed.maxNodes <= 0) {
                return new Outcome(null, "许可证的用户/节点上限必须是正数");
            }
            if (parsed.expiresAt.isBefore(parsed.issuedAt)) {
                return new Outcome(null, "许可证的到期日早于签发日");
            }
            return new Outcome(parsed, null);
        } catch (RuntimeException e) {
            return new Outcome(null, "许可证载荷字段无法解析: " + e.getMessage());
        }
    }

    /** 密钥指纹：够运维对上"库里这张是哪把钥匙"，不足以外推密钥本身 */
    public static String fingerprint(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return "";
        }
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(licenseKey.trim().getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
        StringBuilder hex = new StringBuilder(12);
        for (int i = 0; i < 6; i++) {
            hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16)).append(Character.forDigit(digest[i] & 0xF, 16));
        }
        return hex.toString();
    }

    private static String join(String edition, String issuedTo, LocalDate issuedAt, LocalDate expiresAt,
                               int maxUsers, int maxNodes) {
        requireNoDelimiter(edition, "版本");
        requireNoDelimiter(issuedTo, "客户名");
        if (issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("签发日与到期日不能为空");
        }
        return edition + "|" + issuedTo + "|" + issuedAt + "|" + expiresAt + "|" + maxUsers + "|" + maxNodes;
    }

    private static void requireNoDelimiter(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value.indexOf('|') >= 0) {
            throw new IllegalArgumentException(label + "不能包含分隔符 |");
        }
    }

    private static void requireSecret(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("签发许可证需要配置 platform.license.secret");
        }
    }

    private static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] sign(String secret, String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用: " + e.getMessage(), e);
        }
    }
}
