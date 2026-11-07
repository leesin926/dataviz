package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 配置期闸门：错配置要在启动时就炸，而不是在第 N 条 SQL 上表现成"某个租户查不到数据"。
 * <p>
 * 每条拒绝都断言两件事：点名的<b>配置键</b>、以及<b>不带口令</b>（这些文案会进启动日志与健康检查）。
 */
class ShardPropertiesTest {

    @Test
    @DisplayName("关掉时不需要实例清单：shard.enabled=false 等于这一层不存在")
    void should_passValidation_when_shardDisabled() {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(false);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("打开但没配实例 → 拒绝并点名 shard.instances")
    void should_reject_when_enabledWithoutInstances() {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("shard.instances")
                .hasMessageContaining("实例清单为空");
    }

    @Test
    @DisplayName("实例 id 重复 → 拒绝（id 是路由键，重复等于两个库抢一个键）")
    void should_reject_when_instanceIdsDuplicate() {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setInstances(Arrays.asList(ShardTestFixtures.instance("ds0"), ShardTestFixtures.instance("ds0")));

        BizException refused = catchThrowableOfType(properties::validate, BizException.class);

        assertThat(refused.getMessage())
                .contains("shard.instances[1].id")
                .contains("重复")
                .doesNotContain(ShardTestFixtures.SECRET);
    }

    @Test
    @DisplayName("实例缺 url / username / id → 分别点名对应键")
    void should_reject_when_instanceFieldsMissing() {
        ShardProperties.Instance noUrl = ShardTestFixtures.instance("ds0");
        noUrl.setUrl("  ");
        ShardProperties.Instance noUser = ShardTestFixtures.instance("ds0");
        noUser.setUsername(null);

        assertThatThrownBy(() -> validateWithInstances(noUrl)).hasMessageContaining(".url");
        assertThatThrownBy(() -> validateWithInstances(noUser)).hasMessageContaining(".username");
        assertThatThrownBy(() -> validateWithInstances(new ShardProperties.Instance()))
                .hasMessageContaining(".id");
    }

    @Test
    @DisplayName("池参数三件事都要挡：非正数、负 idle、idle > max")
    void should_reject_when_poolSettingsContradict() {
        ShardProperties.Instance smallMax = ShardTestFixtures.instance("ds0");
        ShardProperties.Pool pool = new ShardProperties.Pool();
        pool.setMaximumPoolSize(2);
        pool.setMinimumIdle(5);
        smallMax.setPool(pool);

        ShardProperties.Instance zeroMax = ShardTestFixtures.instance("ds0");
        zeroMax.setPool(new ShardProperties.Pool());
        zeroMax.getPool().setMaximumPoolSize(0);

        ShardProperties.Instance negativeIdle = ShardTestFixtures.instance("ds0");
        negativeIdle.setPool(new ShardProperties.Pool());
        negativeIdle.getPool().setMinimumIdle(-1);

        ShardProperties.Instance shortTimeout = ShardTestFixtures.instance("ds0");
        shortTimeout.setPool(new ShardProperties.Pool());
        shortTimeout.getPool().setConnectionTimeout(0L);

        assertThatThrownBy(() -> validateWithInstances(smallMax)).hasMessageContaining(".pool.minimum-idle");
        assertThatThrownBy(() -> validateWithInstances(zeroMax)).hasMessageContaining(".pool.maximum-pool-size");
        assertThatThrownBy(() -> validateWithInstances(negativeIdle)).hasMessageContaining(".pool.minimum-idle");
        assertThatThrownBy(() -> validateWithInstances(shortTimeout)).hasMessageContaining(".pool.connection-timeout");
    }

    @Test
    @DisplayName("规则里的表名形状不合法一律拒绝（这些名字会拼进 SQL 文本）")
    void should_reject_when_tableNameCouldBreakSqlShape() {
        assertThatThrownBy(() -> validateWithRule(ShardTestFixtures.rule("Alert_Event")))
                .hasMessageContaining("shard.rules[0].table");
        assertThatThrownBy(() -> validateWithRule(ShardTestFixtures.rule("logs; DROP TABLE x")))
                .hasMessageContaining("不合法");
        assertThatThrownBy(() -> validateWithRule(ShardTestFixtures.rule("1abc")))
                .hasMessageContaining("不合法");
        assertThatThrownBy(() -> validateWithRule(ShardTestFixtures.rule("")))
                .hasMessageContaining("为空");
    }

    @Test
    @DisplayName("同一张表两条规则 → 拒绝（两条规则的桶数/粒度可以同时成立，路由就有两个答案）")
    void should_reject_when_sameTableHasTwoRules() {
        ShardProperties.Rule first = ShardTestFixtures.rule("alert_event");
        ShardProperties.Rule second = ShardTestFixtures.rule("alert_event",
                ShardMode.DAY, ShardRouteBy.TENANT, 2);

        assertThatThrownBy(() -> {
            ShardProperties properties = base();
            properties.setRules(Arrays.asList(first, second));
            properties.validate();
        }).hasMessageContaining("一张逻辑表只能有一条规则");
    }

    @Test
    @DisplayName("mode=NONE 且 route-by=NONE 的规则什么都不做 → 拒绝（配置里留它只会误导排查）")
    void should_reject_when_ruleDoesNothing() {
        assertThatThrownBy(() -> validateWithRule(
                ShardTestFixtures.rule("alert_event", ShardMode.NONE, ShardRouteBy.NONE, 2)))
                .hasMessageContaining("无意义");
    }

    @Test
    @DisplayName("buckets 越界（0 或大于实例数）→ 拒绝，报错点名表名与实例数")
    void should_reject_when_bucketsOutOfRange() {
        assertThatThrownBy(() -> validateWithRule(
                ShardTestFixtures.rule("alert_event", ShardMode.MONTH, ShardRouteBy.TENANT, 0)))
                .hasMessageContaining("buckets=0");
        assertThatThrownBy(() -> validateWithRule(
                ShardTestFixtures.rule("alert_event", ShardMode.MONTH, ShardRouteBy.TENANT, 3)))
                .hasMessageContaining("超过实例数")
                .hasMessageContaining("2");
    }

    @Test
    @DisplayName("pre-create-months：负数拒绝；DAY 模式还配提前月数拒绝")
    void should_reject_when_preCreateMonthsMisused() {
        ShardProperties.Rule negative = ShardTestFixtures.rule("alert_event");
        negative.setPreCreateMonths(-1);
        ShardProperties.Rule dayWithMonths = ShardTestFixtures.rule("sys_login_log",
                ShardMode.DAY, ShardRouteBy.TENANT, 2);
        dayWithMonths.setPreCreateMonths(2);

        assertThatThrownBy(() -> validateWithRule(negative)).hasMessageContaining("不能为负");
        assertThatThrownBy(() -> validateWithRule(dayWithMonths)).hasMessageContaining("mode=DAY");
    }

    @Test
    @DisplayName("time-column 也走同一套形状白名单")
    void should_reject_when_timeColumnShapeIsIllegal() {
        ShardProperties.Rule rule = ShardTestFixtures.rule("alert_event");
        rule.setTimeColumn("create time");

        assertThatThrownBy(() -> validateWithRule(rule)).hasMessageContaining(".time-column");
    }

    @Test
    @DisplayName("shard.max-shards-per-read < 1 拒绝：0 会让每次区间读都变成空结果")
    void should_reject_when_shardCapIsNotPositive() {
        ShardProperties properties = base();
        properties.setMaxShardsPerRead(0);

        assertThatThrownBy(properties::validate).hasMessageContaining("shard.max-shards-per-read");
    }

    @Test
    @DisplayName("instances / rules 为 null 时按空清单处理，不空指针")
    void should_tolerateNullLists_when_propertiesFreshlyBound() {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setInstances(null);
        properties.setRules(null);

        assertThatThrownBy(properties::validate).hasMessageContaining("实例清单为空");
        assertThat(properties.getInstances()).isEmpty();
        assertThat(properties.getRules()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 查询侧
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ruleOf 大小写不敏感，null / 未知表给 null")
    void should_findRuleCaseInsensitively_when_lookingUp() {
        ShardProperties properties = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));

        assertThat(properties.ruleOf("ALERT_EVENT")).isNotNull();
        assertThat(properties.ruleOf("alert_event")).isNotNull();
        assertThat(properties.ruleOf("sys_tenant")).isNull();
        assertThat(properties.ruleOf(null)).isNull();
        properties.setRules(null);
        assertThat(properties.ruleOf("alert_event")).isNull();
    }

    @Test
    @DisplayName("默认实例就是清单里的第一个：非分片表永远落它")
    void should_useFirstInstanceAsDefault_when_instancesOrdered() {
        ShardProperties properties = new ShardProperties();
        properties.setInstances(Arrays.asList(ShardTestFixtures.instance("keepme"), ShardTestFixtures.instance("ds1")));

        assertThat(properties.defaultInstanceId()).isEqualTo("keepme");
        assertThat(properties.instanceIds()).containsExactly("keepme", "ds1");
    }

    @Test
    @DisplayName("空清单问默认实例 → 拒绝，报错点名配置键")
    void should_reject_when_defaultInstanceRequestedWithoutInstances() {
        assertThatThrownBy(new ShardProperties()::defaultInstanceId)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("shard.instances");
    }

    @Test
    @DisplayName("预建表默认关：cron 有默认值，enabled 默认 false")
    void should_beDisabledByDefault_when_preCreateFresh() {
        ShardProperties.PreCreate precreate = new ShardProperties.PreCreate();

        assertThat(precreate.isEnabled()).isFalse();
        assertThat(precreate.getCron()).isEqualTo("0 15 3 * * ?");
        assertThat(precreate.getDdlTemplate()).isNull();
        assertThat(new ShardProperties().getPrecreate().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("默认规则口径：MONTH + TENANT + buckets 1 + 提前 1 个月 + create_time")
    void should_haveSafeDefaults_when_ruleNotConfigured() {
        ShardProperties.Rule rule = new ShardProperties.Rule();

        assertThat(rule.getMode()).isEqualTo(ShardMode.MONTH);
        assertThat(rule.getRouteBy()).isEqualTo(ShardRouteBy.TENANT);
        assertThat(rule.getBuckets()).isEqualTo(1);
        assertThat(rule.getPreCreateMonths()).isEqualTo(1);
        assertThat(rule.getTimeColumn()).isEqualTo("create_time");
    }

    @Test
    @DisplayName("规则里出现 null 元素：拒绝并点名下标（配置绑定出 null 是常见的 yaml 手滑）")
    void should_reject_when_listsContainNullElements() {
        ShardProperties rules = base();
        rules.setRules(new ArrayList<ShardProperties.Rule>(Collections.<ShardProperties.Rule>singletonList(null)));

        assertThatThrownBy(rules::validate).hasMessageContaining("shard.rules[0]");

        ShardProperties instances = new ShardProperties();
        instances.setEnabled(true);
        instances.setInstances(Arrays.asList(ShardTestFixtures.instance("ds0"), null));

        assertThatThrownBy(instances::validate).hasMessageContaining("shard.instances[1]");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static ShardProperties base() {
        return ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
    }

    private static void validateWithInstances(ShardProperties.Instance... instances) {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setInstances(Arrays.asList(instances));
        properties.validate();
    }

    private static void validateWithRule(ShardProperties.Rule rule) {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setInstances(Arrays.asList(ShardTestFixtures.instance("ds0"), ShardTestFixtures.instance("ds1")));
        properties.setRules(Arrays.asList(rule));
        properties.validate();
    }
}
