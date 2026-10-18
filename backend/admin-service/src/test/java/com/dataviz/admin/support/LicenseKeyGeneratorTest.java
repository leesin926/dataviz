package com.dataviz.admin.support;

import java.time.LocalDate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 手工生成演示许可证密钥（只在测试类路径，打进不了 jar）。
 *
 * <p>为什么需要它：验签要求服务端 {@code platform.license.secret} 与签发用的是同一把密钥，
 * 而代码里刻意没有默认密钥 —— 所以"随便粘一串字符"永远激活不了，测试这一步必须先有一张真签过的钥匙。</p>
 *
 * <p>用法：在 IDEA 里直接运行下面这个方法（它带 @Disabled，所以不会被 {@code mvn test} 顺带跑出来），
 * 把打印出来的密钥粘到管理端许可证页；服务端要把同一把密钥放进环境变量 {@code LICENSE_SECRET} 后重启 admin-service(8093)。</p>
 */
class LicenseKeyGeneratorTest {

    @Test
    @Disabled("只在手工要演示密钥时运行：mvn test 里跑它没有意义，而且会往构建日志里印密钥")
    @DisplayName("打印一张有效 + 一张已过期的演示密钥")
    void printSampleKeys() {
        String secret = System.getenv("LICENSE_SECRET");
        boolean fromEnv = secret != null && !secret.trim().isEmpty();
        if (!fromEnv) {
            secret = "demo-signing-secret";
        }
        LocalDate today = LocalDate.now();

        String active = LicenseCodec.issue(secret, "PRO", "演示客户", today, today.plusYears(1), 50, 3);
        String expired = LicenseCodec.issue(secret, "STANDARD", "演示客户", today.minusYears(2), today.minusDays(1), 10, 1);

        System.out.println("签发密钥 = " + secret + (fromEnv ? "（取自 LICENSE_SECRET）" : "（未设 LICENSE_SECRET，用内置演示密钥）"));
        System.out.println("有效一年 [daysLeft>0]：" + active);
        System.out.println("昨天到期 [expired]  ：" + expired);
        System.out.println("指纹 " + LicenseCodec.fingerprint(active) + " / " + LicenseCodec.fingerprint(expired));
    }
}
