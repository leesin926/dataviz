package com.dataviz.common.mybatis.shard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桶位哈希的<b>跨实现一致性</b>钉桩（阶段 PI3 的判据）。
 * <p>
 * 这条钉的不是"分布均不均匀"，而是<b>同一租户在 Java 与 MySQL 两边算出同一个桶</b>。回填脚本用
 * MySQL 的 {@code CONV(LEFT(MD5(...),8),16,10)} 决定一行落哪个实例，应用写入用
 * {@link ShardRouter#stableHash} —— 两边不一致时数据在盘上但按任何条件都读不到，而且是静默的。
 * <p>
 * 下面 {@link #MEASURED} 与 {@link #PERIODS} 两列 hex8/h 是 <b>2026-11-07 在真库上实测</b>的
 * （ds0，MySQL 8），式子：
 * <pre>
 * SELECT v, LEFT(MD5(CAST(v AS CHAR)),8), CONV(LEFT(MD5(CAST(v AS CHAR)),8),16,10),
 *        MOD(CAST(CONV(LEFT(MD5(CAST(v AS CHAR)),8),16,10) AS UNSIGNED),2),
 *        MOD(CAST(CONV(LEFT(MD5(CAST(v AS CHAR)),8),16,10) AS UNSIGNED),4) FROM (...);
 * </pre>
 * 改哈希式子必然撞红这里——这正是目的。
 */
class ShardBucketParityTest {

    /** tenant_id, MySQL 给的 MD5 前 8 个十六进制字符, 无符号十进制, buckets=2, buckets=4（全部实测）。 */
    private static final String[][] MEASURED = {
            {"0", "cfcd2084", "3486326916", "0", "0"},
            {"1", "c4ca4238", "3301589560", "0", "0"},
            {"2", "c81e728d", "3357438605", "1", "1"},
            {"9", "45c48cce", "1170509006", "0", "2"},
            {"10", "d3d94468", "3554231400", "0", "0"},
            {"88", "2a38a4a9", "708355241", "1", "1"},
            {"901", "892c91e0", "2301399520", "0", "0"},
            {"902", "b6a1085a", "3064006746", "0", "2"},
            {"903", "aa169b49", "2853608265", "1", "1"},
            {"904", "f47d0ad3", "4101835475", "1", "3"},
            {"905", "f57a2f55", "4118425429", "1", "1"},
            {"1234567890123", "ee769719", "4000749337", "1", "1"},
    };

    @Test
    @DisplayName("逐个租户比对：Java 哈希与 MySQL 实测的 hex8/十进制/桶位三项全等")
    void should_matchMySqlMeasuredValues_when_hashingTenantIds() {
        for (String[] row : MEASURED) {
            long javaHash = ShardRouter.stableHash(row[0]);

            assertThat(javaHash)
                    .as("tenant=%s 的哈希必须等于 MySQL CONV(LEFT(MD5(...),8),16,10)=%s", row[0], row[2])
                    .isEqualTo(new BigInteger(row[1], 16).longValue())
                    .isEqualTo(Long.parseLong(row[2]));
            assertThat(javaHash % 2).as("tenant=%s buckets=2", row[0]).isEqualTo(Long.parseLong(row[3]));
            assertThat(javaHash % 4).as("tenant=%s buckets=4", row[0]).isEqualTo(Long.parseLong(row[4]));
        }
    }

    @ParameterizedTest(name = "Long {0} 与字符串 \"{0}\" 算出同一个桶")
    @ValueSource(strings = {"1", "901", "902", "903", "904", "905", "1234567890123"})
    @DisplayName("路由值是 Long 还是 String 不影响桶位（Service 层两种形态都会出现）")
    void should_agreeAcrossTypes_when_routeValueIsLongOrString(String tenant) {
        assertThat(ShardRouter.stableHash(Long.parseLong(tenant))).isEqualTo(ShardRouter.stableHash(tenant));

        ShardRouter router = ShardTestFixtures.router("openapi_log");
        assertThat(router.instanceIndex("openapi_log", Long.parseLong(tenant)))
                .isEqualTo(router.instanceIndex("openapi_log", tenant));
    }

    @Test
    @DisplayName("演示租户实测落点：1/901/902 → ds0，903/904/905 → ds1（README 的同一张表）")
    void should_splitDemoTenants_asDocumentedInReadme() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThat(router.instanceIndex("openapi_log", 1L)).isZero();
        assertThat(router.instanceIndex("openapi_log", 901L)).isZero();
        assertThat(router.instanceIndex("openapi_log", 902L)).isZero();
        assertThat(router.instanceIndex("openapi_log", 903L)).isEqualTo(1);
        assertThat(router.instanceIndex("openapi_log", 904L)).isEqualTo(1);
        assertThat(router.instanceIndex("openapi_log", 905L)).isEqualTo(1);

        assertThat(router.instanceId(router.instanceIndex("openapi_log", 903L))).isEqualTo("ds1");
        assertThat(router.instanceId(router.instanceIndex("openapi_log", 901L))).isEqualTo("ds0");
    }

    @Test
    @DisplayName("两个演示租户确实散到不同实例（分库真的分开了，不是全都落 ds0）")
    void should_spreadAcrossInstances_when_severalDemoTenantsRouted() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        assertThat(router.instanceIndex("alert_event", 1L))
                .isNotEqualTo(router.instanceIndex("alert_event", 903L));
    }

    @ParameterizedTest(name = "{1} 模式的周期 {0}：MySQL 实测 {2}")
    @CsvSource({
            "202611, MONTH, 08a617a4, 145102756",
            "202612, MONTH, c1e7eb71, 3253201777",
            "202701, MONTH, 805f5685, 2153731717",
            "20261106, DAY, 3e7b509d, 1048268957",
            "20261231, DAY, 8bcd94d5, 2345506005",
    })
    @DisplayName("routeBy=NONE 的实例轮转键也走同一条哈希，且字符串与日期两种形态同答案")
    void should_useSameHash_when_rotatingInstancesByPeriod(String period, String modeName, String hex8, String decimal) {
        ShardMode mode = ShardMode.valueOf(modeName);
        ShardRouter router = ShardTestFixtures.routerOf(
                ShardTestFixtures.properties(2, ShardTestFixtures.rule("openapi_log", mode, ShardRouteBy.NONE, 2)));

        assertThat(ShardRouter.stableHash(period)).isEqualTo(new BigInteger(hex8, 16).longValue())
                .isEqualTo(Long.parseLong(decimal));
        // 同一个周期用 LocalDate 传也得落同一个实例：字符串与日期两种形态不能算出两个答案
        assertThat(router.instanceIndex("openapi_log", period))
                .isEqualTo(router.instanceIndex("openapi_log", ShardRouter.toLocalDate(period, "openapi_log")));
    }

    @Test
    @DisplayName("MD5 前 4 字节按大端拼：无符号，最高位置 1 也不许变负数（否则与 MySQL 的 MOD 反号）")
    void should_readFirstFourBytesBigEndian_when_leadingIntComputed() {
        assertThat(ShardRouter.md5LeadingInt("1")).isEqualTo(0xc4ca4238L);
        assertThat(ShardRouter.md5LeadingInt("903")).isEqualTo(0xaa169b49L);
        assertThat(ShardRouter.md5LeadingInt("904")).isEqualTo(0xf47d0ad3L).isPositive();
        assertThat(ShardRouter.md5LeadingInt("902")).isEqualTo(0xb6a1085aL).isPositive();
    }

    @Test
    @DisplayName("非 ASCII 路由值按 UTF-8 取字节（MySQL 会话 utf8mb4 同字节）")
    void should_encodeUtf8_when_routeValueHasNonAscii() {
        assertThat(ShardRouter.stableHash("租户")).isEqualTo(ShardRouter.md5LeadingInt("租户"));
        assertThat(ShardRouter.stableHash("租 户")).isNotEqualTo(ShardRouter.stableHash("租户"));
    }

    @Test
    @DisplayName("曾经用错的 String.hashCode() 现在算不出桶位：钉住这次修复不是空改动")
    void should_notEqualJavaHashCode_when_contractIsMd5() {
        assertThat(ShardRouter.stableHash("903")).isNotEqualTo((long) "903".hashCode());
        assertThat(ShardRouter.stableHash("1")).isNotEqualTo((long) "1".hashCode());
        assertThat(ShardRouter.stableHash("901")).isNotEqualTo((long) "901".hashCode());
    }
}
