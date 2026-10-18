package com.dataviz.admin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LicenseCodec} 的单元测试。
 * <p>这个类是许可证的唯一信任边界，所以测的是"什么样的密钥会被放行"，不是"方法有没有被调用"。
 * 测试里需要自己构造密钥（改段数、改上限）的那些用例，正是攻击面本身 —— 用 issue() 造不出它们。</p>
 */
class LicenseCodecTest {

    private static final String SECRET = "test-signing-secret";
    private static final LocalDate ISSUED = LocalDate.of(2026, 1, 1);
    private static final LocalDate EXPIRES = LocalDate.of(2027, 1, 1);

    /** 手工签一张密钥：用于 issue() 拒绝生成的畸形载荷（段数不对、上限为 0 等） */
    private static String craft(String secret, String rawPayload) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "DVIZ1." + encoded + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String validKey() {
        return LicenseCodec.issue(SECRET, "PRO", "ACME", ISSUED, EXPIRES, 50, 3);
    }

    @Test
    @DisplayName("签发后原样验签 - 同密钥应还原全部字段")
    void should_restoreEveryField_when_verifiedWithSameSecret() {
        LicenseCodec.Outcome outcome = LicenseCodec.verify(SECRET, validKey());

        assertThat(outcome.isValid()).isTrue();
        assertThat(outcome.error).isNull();
        assertThat(outcome.payload.edition).isEqualTo("PRO");
        assertThat(outcome.payload.issuedTo).isEqualTo("ACME");
        assertThat(outcome.payload.issuedAt).isEqualTo(ISSUED);
        assertThat(outcome.payload.expiresAt).isEqualTo(EXPIRES);
        assertThat(outcome.payload.maxUsers).isEqualTo(50);
        assertThat(outcome.payload.maxNodes).isEqualTo(3);
    }

    @Test
    @DisplayName("换一把密钥 - 签名不匹配，且不返回载荷")
    void should_reject_when_secretDiffers() {
        LicenseCodec.Outcome outcome = LicenseCodec.verify("other-secret", validKey());

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.error).contains("签名不匹配");
        assertThat(outcome.payload).isNull();
    }

    @Test
    @DisplayName("改写载荷段（Standard 冒充 PRO）- 签名不匹配")
    void should_reject_when_payloadTampered() {
        String key = validKey();
        String[] parts = key.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "ENTERPRISE|ACME|2026-01-01|2027-01-01|99999|99".getBytes(StandardCharsets.UTF_8));

        LicenseCodec.Outcome outcome = LicenseCodec.verify(SECRET, "DVIZ1." + forgedPayload + "." + parts[2]);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.error).contains("签名不匹配");
    }

    @Test
    @DisplayName("密钥为空或服务端未配签发密钥 - 都拒绝，而不是当成没有许可证放过")
    void should_reject_when_keyOrSecretMissing() {
        assertThat(LicenseCodec.verify(SECRET, "  ").error).contains("密钥为空");
        assertThat(LicenseCodec.verify("", validKey()).error).contains("未配置许可证签发密钥");
        assertThat(LicenseCodec.verify(SECRET, null).error).contains("密钥为空");
    }

    @Test
    @DisplayName("段数或前缀不对 - 给出能直接看的原因")
    void should_reject_when_keyMalformed() {
        assertThat(LicenseCodec.verify(SECRET, "DVIZ1.abc").error).contains("格式不对");
        assertThat(LicenseCodec.verify(SECRET, "DVIZ2.abc.def").error).contains("前缀不认识");
        assertThat(LicenseCodec.verify(SECRET, "DVIZ1.abc.!!not-base64!!").error).contains("签名段不是合法的 base64url");
    }

    @Test
    @DisplayName("载荷字段数不对 - 即使签名正确也拒绝")
    void should_reject_when_segmentCountWrong() {
        LicenseCodec.Outcome outcome = LicenseCodec.verify(SECRET, craft(SECRET, "PRO|ACME|2026-01-01|2027-01-01|50"));

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.error).contains("字段数不对");
    }

    @Test
    @DisplayName("上限为 0 或负数 - 拒绝")
    void should_reject_when_limitsNotPositive() {
        assertThat(LicenseCodec.verify(SECRET, craft(SECRET, "PRO|ACME|2026-01-01|2027-01-01|0|3")).error)
                .contains("必须是正数");
        assertThat(LicenseCodec.verify(SECRET, craft(SECRET, "PRO|ACME|2026-01-01|2027-01-01|50|-1")).error)
                .contains("必须是正数");
    }

    @Test
    @DisplayName("到期早于签发 - 拒绝")
    void should_reject_when_expiryBeforeIssue() {
        LicenseCodec.Outcome outcome = LicenseCodec.verify(SECRET, craft(SECRET, "PRO|ACME|2027-01-01|2026-01-01|50|3"));

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.error).contains("到期日早于签发日");
    }

    @Test
    @DisplayName("已过期但签名正确 - 编解码层放行，过期由服务层判")
    void should_acceptExpiredKey_when_signatureValid() {
        String key = LicenseCodec.issue(SECRET, "PRO", "ACME", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 50, 3);

        assertThat(LicenseCodec.verify(SECRET, key).isValid()).isTrue();
    }

    @Test
    @DisplayName("载荷里出现分隔符 - 签发阶段就拒绝，不留歧义")
    void should_rejectDelimiter_when_issuing() {
        LocalDate today = LocalDate.of(2026, 1, 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> LicenseCodec.issue(SECRET, "PRO", "AC|ME", today, EXPIRES, 50, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分隔符");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LicenseCodec.issue(SECRET, "PRO", "ACME", null, EXPIRES, 50, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("没有密钥就不能签发 - 不回退到内置密钥")
    void should_fail_when_issuingWithoutSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> LicenseCodec.issue("  ", "PRO", "ACME", ISSUED, EXPIRES, 50, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.license.secret");
    }

    @Test
    @DisplayName("指纹 - 定长、稳定、随密钥变化，且不含密钥材料")
    void should_beStableAndKeyIndependent_when_fingerprinting() {
        String key = validKey();

        assertThat(LicenseCodec.fingerprint(key)).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(LicenseCodec.fingerprint(key)).isEqualTo(LicenseCodec.fingerprint("  " + key + "  "));
        assertThat(LicenseCodec.fingerprint(LicenseCodec.issue("other-secret", "PRO", "ACME", ISSUED, EXPIRES, 50, 3)))
                .isNotEqualTo(LicenseCodec.fingerprint(key));
        assertThat(LicenseCodec.fingerprint(null)).isEmpty();
    }
}
