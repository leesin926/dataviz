package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShardRouter} 的纯函数判据：哪个实例、哪张物理表、一次区间读扫哪些分片。
 * <p>
 * 全部不碰 Docker、不碰 Spring。这一层的错误代价是数据落错库/落错表（事后按条件捞不回来），
 * 所以每条拒绝都要钉住<b>文案点名的对象</b>，而不只是"抛了异常"。
 */
class ShardRouterTest {

    @AfterEach
    void clearContext() {
        ShardContext.clear();
    }

    // ------------------------------------------------------------------
    // 实例路由
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buckets=1 时任何租户都落 0 号实例（只分表不分库的配法）")
    void should_alwaysReturnZero_when_bucketsIsOne() {
        ShardRouter router = ShardTestFixtures.routerOf(ShardTestFixtures.properties(1,
                ShardTestFixtures.rule("openapi_log", ShardMode.MONTH, ShardRouteBy.TENANT, 1)));

        assertThat(router.instanceIndex("openapi_log", 1L)).isZero();
        assertThat(router.instanceIndex("openapi_log", 903L)).isZero();
    }

    @ParameterizedTest(name = "租户 {0} → 实例下标 {1}")
    @CsvSource({"1,0", "901,0", "902,0", "903,1", "904,1", "905,1"})
    @DisplayName("租户到实例的下标（分库真的按租户散开）")
    void should_mapTenantsToIndexes_when_routeByTenant(long tenantId, int expectedIndex) {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThat(router.instanceIndex("openapi_log", tenantId)).isEqualTo(expectedIndex);
    }

    @Test
    @DisplayName("同一个租户重复算 200 次永远是同一个实例（路由必须是纯函数）")
    void should_beDeterministic_when_sameTenantHashedRepeatedly() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        for (int i = 0; i < 200; i++) {
            assertThat(router.instanceIndex("alert_event", 903L)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("实例下标越界拒绝并点名实例清单长度（不是数组越界）")
    void should_rejectWithMessage_when_instanceIndexOutOfRange() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        assertThatThrownBy(() -> router.instanceId(2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("越界")
                .hasMessageContaining("2");
        assertThatThrownBy(() -> router.instanceId(-1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("越界");
    }

    @Test
    @DisplayName("没配规则的表来问路由 → 拒绝，并点名这张表")
    void should_reject_when_tableHasNoShardRule() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        assertThatThrownBy(() -> router.instanceIndex("sys_user", 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("sys_user")
                .hasMessageContaining("没有分片规则");
    }

    @ParameterizedTest(name = "空租户 [{0}] 被拒")
    @ValueSource(strings = {"", "   "})
    @DisplayName("租户为空/空白一律拒绝：写进去就再也按租户捞不回来")
    void should_rejectBlankTenant_when_routeValueIsBlank(String blank) {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThatThrownBy(() -> router.instanceIndex("openapi_log", blank))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("tenant_id")
                .hasMessageContaining("为空");
        assertThatThrownBy(() -> router.instanceIndex("openapi_log", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("为空");
    }

    @Test
    @DisplayName("不支持的路由值类型点名实际类型，并提示转成字符串")
    void should_rejectUnsupportedRouteValueType_when_valueIsPoJo() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThatThrownBy(() -> router.instanceIndex("openapi_log", new Object()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类型不支持")
                .hasMessageContaining("java.lang.Object")
                .hasMessageContaining("toString()");
        assertThatThrownBy(() -> router.instanceIndex("openapi_log", new byte[]{1}))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类型不支持");
    }

    @Test
    @DisplayName("字符与数字形态都能算：Character/Integer/Long/String 同一条路")
    void should_acceptNumericCharacterAndString_when_routing() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThat(router.instanceIndex("openapi_log", 903)).isEqualTo(1);
        assertThat(router.instanceIndex("openapi_log", "903")).isEqualTo(1);
        assertThat(ShardRouter.stableHash('1')).isEqualTo(ShardRouter.stableHash("1"));
    }

    @Test
    @DisplayName("routeBy=NONE：按后缀轮转实例，同一月永远同一实例，跨月可以换实例")
    void should_rotateByPeriod_when_routeByNone() {
        ShardRouter router = ShardTestFixtures.routerOf(ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("openapi_log", ShardMode.MONTH, ShardRouteBy.NONE, 2)));

        // 实测：202611 → 桶 0，202612 → 桶 1（ShardBucketParityTest 钉住数值）
        assertThat(router.instanceIndex("openapi_log", "202611")).isZero();
        assertThat(router.instanceIndex("openapi_log", YearMonth.of(2026, 11).atDay(1))).isZero();
        assertThat(router.instanceIndex("openapi_log", "202612")).isEqualTo(1);
        // buckets=1 的退化配法不该去算哈希
        ShardRouter single = ShardTestFixtures.routerOf(ShardTestFixtures.properties(1,
                ShardTestFixtures.rule("openapi_log", ShardMode.MONTH, ShardRouteBy.NONE, 1)));
        assertThat(single.instanceIndex("openapi_log", "202612")).isZero();
    }

    @Test
    @DisplayName("routeBy=NONE 但没给时间值 → 拒绝（时间值是它的唯一路由维度）")
    void should_reject_when_routeByNoneAndNoTimeValue() {
        ShardRouter router = ShardTestFixtures.routerOf(ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("openapi_log", ShardMode.MONTH, ShardRouteBy.NONE, 2)));

        assertThatThrownBy(() -> router.instanceIndex("openapi_log", (Object) null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("时间值");
    }

    // ------------------------------------------------------------------
    // 时间后缀
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{1} 模式的后缀 = {0}")
    @CsvSource({
            "_202611, MONTH, 2026-11-06",
            "_202611, MONTH, 2026-11-06T23:59:59",
            "_202611, MONTH, 2026-11-06 00:00:00",
            "_202611, MONTH, 2026-11",
            "_202611, MONTH, 202611",
            "_202611, MONTH, 20261106",
            "_202611, MONTH, 2026-11-30",
            "_202612, MONTH, 2026-12-01",
            "_20261106, DAY, 2026-11-06",
            "_20261106, DAY, 20261106",
    })
    @DisplayName("同一天的任意时刻都落同一张表；六种写法（含紧凑形）都得同一个后缀")
    void should_produceSameSuffixForTheSamePeriod_when_timeValueFormatsDiffer(String expectedSuffix,
                                                                              String modeName, String timeValue) {
        ShardMode mode = ShardMode.valueOf(modeName);
        ShardProperties.Rule rule = ShardTestFixtures.rule("alert_event", mode, ShardRouteBy.TENANT, 2);

        assertThat(ShardRouter.suffix(rule, timeValue)).isEqualTo(expectedSuffix);
    }

    @Test
    @DisplayName("Date/Timestamp/LocalDateTime/LocalDate 四种运行时形态后缀一致")
    void should_agreeAcrossTimeTypes_when_valueIsDate() {
        ShardProperties.Rule month = ShardTestFixtures.rule("alert_event", ShardMode.MONTH, ShardRouteBy.TENANT, 2);
        LocalDateTime local = LocalDateTime.of(2026, 11, 6, 10, 15, 30);
        Timestamp timestamp = Timestamp.valueOf(local);
        Date date = new Date(timestamp.getTime());

        assertThat(ShardRouter.suffix(month, local)).isEqualTo("_202611");
        assertThat(ShardRouter.suffix(month, local.toLocalDate())).isEqualTo("_202611");
        assertThat(ShardRouter.suffix(month, timestamp)).isEqualTo("_202611");
        assertThat(ShardRouter.suffix(month, date)).isEqualTo("_202611");
    }

    @Test
    @DisplayName("mode=NONE 后缀恒为空串（只分库不分表）")
    void should_returnEmptySuffix_when_modeIsNone() {
        ShardProperties.Rule rule = ShardTestFixtures.rule("monitor_metric",
                ShardMode.NONE, ShardRouteBy.TENANT, 2);

        assertThat(ShardRouter.suffix(rule, "2026-11-06")).isEmpty();
        assertThat(ShardRouter.suffix(null, "2026-11-06")).isEmpty();
    }

    @ParameterizedTest(name = "坏时间值 [{0}] 被拒且报错带原文")
    @ValueSource(strings = {"2026-13-01", "abc", "11/2026", "20261", "99999999999999", "2026/11/06", "2026-11-06X10:00"})
    @DisplayName("解析不了的时间串一律拒绝，报错里带上原文（运维照文案改就能对上）")
    void should_rejectUnparseableTime_when_timeStringIsMalformed(String raw) {
        assertThatThrownBy(() -> ShardRouter.toLocalDate(raw, "alert_event"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("alert_event")
                .hasMessageContaining("无法解析")
                // 报错里的原文带引号：不带引号的话 "20261" 会被提示语中的示例串蒙过去
                .hasMessageContaining("\"" + raw + "\"");
    }

    @Test
    @DisplayName("带时间部分但时分写歪的串按日历日收下：后缀只看天，不看钟点")
    void should_ignoreTimeTail_when_valueIsFullIsoDateTime() {
        // 这是刻意的宽松：分片只看日历日，"2026-11-06T99:00:00" 里的钟点再怎么离谱都不影响落哪张月表。
        // 把它钉下来是因为"接受什么"只能有一种说法，日后收紧要改这条断言而不是悄悄改解析器。
        assertThat(ShardRouter.toLocalDate("2026-11-06T99:00:00", "alert_event"))
                .isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(ShardRouter.toLocalDate("2026-11-06 10:15:30", "alert_event"))
                .isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    @DisplayName("时间值类型不支持时按类型名拒绝（不接受 epoch 毫秒数）")
    void should_rejectUnsupportedTimeType_when_valueIsLongEpoch() {
        assertThatThrownBy(() -> ShardRouter.toLocalDate(System.currentTimeMillis(), "alert_event"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类型不支持")
                .hasMessageContaining("java.lang.Long");
        assertThatThrownBy(() -> ShardRouter.toLocalDate(null, "alert_event"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("为空");
    }

    // ------------------------------------------------------------------
    // 物理表名
    // ------------------------------------------------------------------

    @Test
    @DisplayName("物理表名 = 逻辑名 + 白名单后缀；后缀形状不对一律拒绝")
    void should_buildPhysicalName_when_suffixShapeIsLegal() {
        assertThat(ShardRouter.physical("alert_event", "_202611")).isEqualTo("alert_event_202611");
        assertThat(ShardRouter.physical("alert_event", "_20261106")).isEqualTo("alert_event_20261106");
        assertThat(ShardRouter.physical("alert_event", "")).isEqualTo("alert_event");
        assertThat(ShardRouter.physical("alert_event", null)).isEqualTo("alert_event");

        assertThatThrownBy(() -> ShardRouter.physical("alert_event", "_2026_11"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("后缀不合法");
        assertThatThrownBy(() -> ShardRouter.physical("alert_event", "; DROP TABLE x"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("后缀不合法");
    }

    @Test
    @DisplayName("逻辑表名形状不合法（大写/注入/超长）在拼 SQL 之前就被拒")
    void should_rejectIllegalLogicalName_when_nameWouldBeConcatenatedIntoSql() {
        assertThatThrownBy(() -> ShardRouter.physical("Audit_Log", "_202611"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("逻辑表名不合法");
        assertThatThrownBy(() -> ShardRouter.physical("logs; DROP TABLE x", "_202611"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("逻辑表名不合法");
        assertThatThrownBy(() -> ShardRouter.physical(null, "_202611"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("逻辑表名不合法");
    }

    @Test
    @DisplayName("逻辑名合规但加后缀超过 MySQL 64 字符上限 → 仍然拒绝（不是让 MySQL 报错）")
    void should_reject_when_physicalNameExceedsIdentifierLimit() {
        StringBuilder longName = new StringBuilder("a");
        for (int i = 0; i < 57; i++) {
            longName.append('b');
        }
        assertThat(ShardRouter.physical(longName.toString(), "")).isEqualTo(longName.toString());

        assertThatThrownBy(() -> ShardRouter.physical(longName.toString(), "_202611"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("后缀不合法");
    }

    // ------------------------------------------------------------------
    // 区间扇出
    // ------------------------------------------------------------------

    @Test
    @DisplayName("三个月区间 × 两个桶 = 6 个分片，新→旧排，同月内 ds0 先于 ds1")
    void should_enumerateAllBucketsNewestFirst_when_scanningAcrossTenants() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        List<ShardTarget> targets = router.targetsOf("alert_event", "2026-09-01", "2026-11-30");

        assertThat(targets).hasSize(6);
        assertThat(targets).extracting(ShardTarget::getSuffix)
                .containsExactly("_202611", "_202611", "_202610", "_202610", "_202609", "_202609");
        assertThat(targets).extracting(ShardTarget::getInstanceId)
                .containsExactly("ds0", "ds1", "ds0", "ds1", "ds0", "ds1");
        assertThat(targets).extracting(ShardTarget::getPhysicalTable)
                .startsWith("alert_event_202611");
    }

    @Test
    @DisplayName("租户内区间读只打该租户所在实例：三个月 = 3 个分片，全在 ds1")
    void should_narrowToSingleInstance_when_routeScopedByTenant() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        List<ShardTarget> targets = router.targetsOf("alert_event", 903L, "2026-09-01", "2026-11-30", true);

        assertThat(targets).hasSize(3);
        assertThat(targets).extracting(ShardTarget::getInstanceId).containsOnly("ds1");
        assertThat(targets).allSatisfy(target ->
                assertThat(target.getInstanceIndex()).isEqualTo(router.instanceIndex("alert_event", 903L)));
    }

    @Test
    @DisplayName("mode=NONE 只出一个周期，但每个桶都要扫（后缀为空 = 逻辑表本身）")
    void should_scanOnePeriodPerBucket_when_modeNone() {
        ShardRouter router = ShardTestFixtures.routerOf(ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("monitor_metric", ShardMode.NONE, ShardRouteBy.TENANT, 2)));

        List<ShardTarget> targets = router.targetsOf("monitor_metric", "2026-01-01", "2026-12-31");

        assertThat(targets).hasSize(2);
        assertThat(targets).extracting(ShardTarget::getSuffix).containsOnly("");
        assertThat(targets).extracting(ShardTarget::getPhysicalTable).containsOnly("monitor_metric");
        assertThat(targets).allSatisfy(target -> {
            assertThat(target.getTimeFrom()).isNull();
            assertThat(target.getTimeTo()).isNull();
        });
    }

    @Test
    @DisplayName("按日分表：单日区间 = 桶数 个分片，边界是当日 0 点到次日 0 点")
    void should_enumerateDaysAndCarryBoundaries_when_modeDay() {
        ShardRouter router = ShardTestFixtures.routerOf(ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("sys_login_log", ShardMode.DAY, ShardRouteBy.TENANT, 2)));

        List<ShardTarget> oneDay = router.targetsOf("sys_login_log", "2026-11-06", "2026-11-06");
        assertThat(oneDay).hasSize(2);
        assertThat(oneDay).extracting(ShardTarget::getSuffix).containsOnly("_20261106");
        assertThat(oneDay.get(0).getTimeFrom()).isEqualTo(LocalDate.of(2026, 11, 6).atStartOfDay());
        assertThat(oneDay.get(0).getTimeTo()).isEqualTo(LocalDate.of(2026, 11, 7).atStartOfDay());

        List<ShardTarget> threeDays = router.targetsOf("sys_login_log", "2026-11-04", "2026-11-06");
        assertThat(threeDays).hasSize(6);
        assertThat(threeDays).extracting(ShardTarget::getSuffix)
                .containsExactly("_20261106", "_20261106", "_20261105", "_20261105", "_20261104", "_20261104");
    }

    @Test
    @DisplayName("月分片边界：首月末次月交界，且月末当天的分片起点仍是 1 号")
    void should_coverWholeMonth_when_monthShard() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        List<ShardTarget> targets = router.targetsOf("alert_event", 903L, "2026-11-15", "2026-12-05", true);

        assertThat(targets).extracting(ShardTarget::getSuffix).containsExactly("_202612", "_202611");
        ShardTarget november = targets.get(1);
        assertThat(november.getTimeFrom()).isEqualTo(YearMonth.of(2026, 11).atDay(1).atStartOfDay());
        assertThat(november.getTimeTo()).isEqualTo(YearMonth.of(2026, 12).atDay(1).atStartOfDay());
        assertThat(targets).extracting(ShardTarget::getInstanceId).containsOnly("ds1");
    }

    @Test
    @DisplayName("跨年区间按月份连续枚举（12 月 + 次年 1 月）")
    void should_crossYearBoundary_when_periodSpansDecember() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        List<ShardTarget> targets = router.targetsOf("alert_event", 1L, "2026-12-01", "2027-02-28", true);

        assertThat(targets).extracting(ShardTarget::getSuffix)
                .containsExactly("_202702", "_202701", "_202612");
    }

    @Test
    @DisplayName("区间反了（from > to）当场拒绝，而不是返回空计划")
    void should_rejectReversedRange_when_fromIsAfterTo() {
        ShardRouter router = ShardTestFixtures.router("alert_event");

        assertThatThrownBy(() -> router.targetsOf("alert_event", "2026-11-30", "2026-11-01"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("区间反了");
    }

    @Test
    @DisplayName("超过 shard.max-shards-per-read：直接算的计划拒绝，扇出执行的计划截断并置 truncated")
    void should_hardFailOrTruncate_when_shardCountExceedsCap() {
        ShardProperties properties = ShardTestFixtures.properties(2, ShardTestFixtures.rule("alert_event"));
        properties.setMaxShardsPerRead(4);
        ShardRouter router = ShardTestFixtures.routerOf(properties);

        assertThatThrownBy(() -> router.targetsOf("alert_event", "2026-06-01", "2026-11-30"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过上限")
                .hasMessageContaining("shard.max-shards-per-read");

        ShardRouter.Scan scan = router.targetsWithin("alert_event", "2026-06-01", "2026-11-30");
        assertThat(scan.getRequiredShards()).isEqualTo(12);
        assertThat(scan.getCap()).isEqualTo(4);
        assertThat(scan.isTruncated()).isTrue();
        assertThat(scan.getTargets()).hasSize(4);
        // 截断只砍尾（保留最新分片），不会给出一个乱序的半截计划
        assertThat(scan.getTargets()).extracting(ShardTarget::getSuffix)
                .containsExactly("_202611", "_202611", "_202610", "_202610");
    }

    @Test
    @DisplayName("上限恰好等于需要时不算截断（边界不off-by-one）")
    void should_notTruncate_when_shardCountEqualsCap() {
        ShardProperties properties = ShardTestFixtures.properties(2, ShardTestFixtures.rule("alert_event"));
        properties.setMaxShardsPerRead(4);
        ShardRouter router = ShardTestFixtures.routerOf(properties);

        ShardRouter.Scan scan = router.targetsWithin("alert_event", "2026-09-01", "2026-10-31");

        assertThat(scan.getRequiredShards()).isEqualTo(4);
        assertThat(scan.isTruncated()).isFalse();
        assertThat(scan.getTargets()).hasSize(4);
    }

    @Test
    @DisplayName("buckets 大于实例清单时扇出按实例数收敛（配置期已挡，这里是兜底：不越界也不漏桶）")
    void should_clampBucketsToInstanceCount_when_bucketsExceedInstances() {
        // 故意不调 validate()：配置期闸门会挡掉这种配法，这里要钉的是"万一绕过闸门"的兜底行为
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setInstances(java.util.Arrays.asList(
                ShardTestFixtures.instance("ds0"), ShardTestFixtures.instance("ds1")));
        properties.setRules(java.util.Arrays.asList(
                ShardTestFixtures.rule("alert_event", ShardMode.MONTH, ShardRouteBy.TENANT, 5)));
        ShardRouter router = new ShardRouter(properties);

        List<ShardTarget> targets = router.targetsOf("alert_event", "2026-11-01", "2026-11-30");

        assertThat(targets).hasSize(2);
        assertThat(targets).extracting(ShardTarget::getInstanceId).containsExactly("ds0", "ds1");
    }

    // ------------------------------------------------------------------
    // 绑定态解析
    // ------------------------------------------------------------------

    @Test
    @DisplayName("闸门落定状态：解析前后 Binding 上的两个字段从 null 变成实例与后缀")
    void should_settleResolvedState_when_bindingResolvedOnce() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        try (ShardContext.ShardScope scope = ShardContext.open("openapi_log", 903L, "2026-11-06")) {
            ShardContext.Binding binding = ShardContext.current();
            assertThat(binding.getResolvedInstanceId()).isNull();
            assertThat(binding.getResolvedSuffix()).isNull();

            assertThat(router.resolveInstanceId(binding)).isEqualTo("ds1");
            assertThat(router.resolveSuffix(binding)).isEqualTo("_202611");

            assertThat(binding.getResolvedInstanceId()).isEqualTo("ds1");
            assertThat(binding.getResolvedSuffix()).isEqualTo("_202611");
            // 再问一次仍是同一份结果（一次请求内不许两次算出两个答案）
            assertThat(router.resolveInstanceId(binding)).isEqualTo("ds1");
        }
        assertThat(ShardContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("非分片表的绑定解析退回默认实例、后缀为空串（配置表不能被分片层挡住）")
    void should_fallBackToDefaultInstance_when_tableIsNotSharded() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        try (ShardContext.ShardScope scope = ShardContext.open("openapi_client", 903L, "2026-11-06")) {
            ShardContext.Binding binding = ShardContext.current();

            assertThat(router.resolveInstanceId(binding)).isEqualTo("ds0");
            assertThat(router.resolveSuffix(binding)).isEmpty();
            assertThat(router.isSharded("openapi_client")).isFalse();
        }
    }

    @Test
    @DisplayName("空绑定直接问路由 → 拒绝并给出 ShardContext.open 的补法")
    void should_rejectNullBinding_when_noContextAtAll() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        assertThatThrownBy(() -> router.resolveInstanceId(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未绑定路由上下文");
        assertThatThrownBy(() -> router.resolveSuffix(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未绑定路由上下文");
    }

    @Test
    @DisplayName("绑定里租户为空 → 解析时报的是租户为空，不是空指针")
    void should_reportBlankTenant_when_bindingHasNoTenantId() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        try (ShardContext.ShardScope scope = ShardContext.open("openapi_log", null, "2026-11-06")) {
            assertThatThrownBy(() -> router.resolveInstanceId(ShardContext.current()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("tenant_id");
        }
    }

    @Test
    @DisplayName("绑定里时间为空 → 后缀算不出来就拒绝（宁可拒绝也不建一张不知道月份的表）")
    void should_reportBlankTime_when_bindingHasNoTimeValue() {
        ShardRouter router = ShardTestFixtures.router("openapi_log");

        try (ShardContext.ShardScope scope = ShardContext.open("openapi_log", 903L, null)) {
            assertThatThrownBy(() -> router.resolveSuffix(ShardContext.current()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("时间值");
        }
    }

    @Test
    @DisplayName("实例清单为空时路由器构造即拒绝（报错点名配置键，不留到第一条 SQL）")
    void should_rejectConstruction_when_instancesAreEmpty() {
        assertThatThrownBy(() -> new ShardRouter(null))
                .isInstanceOf(IllegalArgumentException.class);

        // enabled=false 时 validate() 不要求实例清单（关掉等于不存在），但路由器没有实例就没法工作
        ShardProperties noInstances = new ShardProperties();
        noInstances.validate();

        assertThatThrownBy(() -> new ShardRouter(noInstances))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("shard.instances");
    }
}
