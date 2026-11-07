package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;

/**
 * 分片闸门（SQL 层）的判据：什么时候放行、什么时候拒绝、拒绝时点不到名字就算失败。
 * <p>
 * 这一层的价值全在"错方向的事不发生"：未绑定的分片表语句如果被放过，MySQL 会按逻辑表名执行，
 * 写进一张没人读的表，或者读出一张空表——两种都表现为"功能正常但数据不对"。
 */
class ShardInnerInterceptorTest {

    private final ShardProperties properties = ShardTestFixtures.twoInstances(
            ShardTestFixtures.rule("alert_event"),
            ShardTestFixtures.rule("alert_notify_log"));
    private final ShardRouter router = ShardTestFixtures.routerOf(properties);
    private final ShardInnerInterceptor guard = new ShardInnerInterceptor(properties, router);

    @AfterEach
    void clearContext() {
        // ThreadLocal 泄漏到下一个测试会假装"已绑定"，这一句是这批测试自身的地基
        ShardContext.clear();
    }

    @Test
    @DisplayName("非分片表一句话都不改：不绑上下文也照常放行")
    void should_ignoreStatement_when_noShardedTable() {
        guard.guard("com.x.selectTenant", "SELECT id, name FROM sys_tenant WHERE id = ?");

        assertThat(ShardContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("空 SQL / null SQL 不当成 violation（MyBatis 自己会在更早的地方失败）")
    void should_skipQuietly_when_sqlIsBlank() {
        guard.guard("com.x.empty", null);
        guard.guard("com.x.empty", "   ");
    }

    @Test
    @DisplayName("分片表未绑上下文 → 拒绝，报错点名表名、两个路由键和补法")
    void should_refuse_when_shardedTableHasNoBinding() {
        assertThatThrownBy(() -> guard.guard("com.x.selectEvents",
                "SELECT id FROM alert_event WHERE tenant_id = 903"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("alert_event")
                .hasMessageContaining("未绑定路由上下文")
                .hasMessageContaining("ShardContext.open")
                .hasMessageContaining("tenant_id")
                .hasMessageContaining("create_time");
    }

    @Test
    @DisplayName("绑定的是别的表 → 照样拒绝（防止拿 A 表的分片键去跑 B 表）")
    void should_refuse_when_bindingIsForAnotherTable() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_notify_log", 903L, "2026-11-06")) {
            assertThatThrownBy(() -> guard.guard("com.x.selectEvents", "SELECT id FROM alert_event"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("alert_event")
                    .hasMessageContaining("未绑定路由上下文");
        }
    }

    @Test
    @DisplayName("放行时把路由状态落定：handler 与数据源读同一份结果")
    void should_settleRouteState_when_bindingMatchesShardedTable() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            guard.guard("com.x.insertEvent", "INSERT INTO alert_event (tenant_id, create_time) VALUES (?, ?)");

            ShardContext.Binding binding = ShardContext.current();
            assertThat(binding.getResolvedInstanceId()).isEqualTo("ds1");
            assertThat(binding.getResolvedSuffix()).isEqualTo("_202611");
        }
    }

    @Test
    @DisplayName("一条语句命中两张分片表 → 当场拒绝跨分片 join，并点名两张表")
    void should_refuseCrossShardJoin_when_twoShardedTablesInOneStatement() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThatThrownBy(() -> guard.guard("com.x.join",
                    "SELECT e.id FROM alert_event e JOIN alert_notify_log n ON n.event_id = e.id"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("多张分片表")
                    .hasMessageContaining("alert_event")
                    .hasMessageContaining("alert_notify_log");
        }
    }

    @Test
    @DisplayName("分片表与配置表 join 允许：只有一张分片表时正常放行")
    void should_allowJoinWithConfigTable_when_onlyOneShardedTable() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            guard.guard("com.x.joinConfig",
                    "SELECT e.id FROM alert_event e JOIN alert_rule r ON r.id = e.rule_id");

            assertThat(ShardContext.current().getResolvedSuffix()).isEqualTo("_202611");
        }
    }

    @Test
    @DisplayName("routeBy=NONE 的规则只要时间：报错提示的是时间值而不是租户")
    void should_hintTimeValue_when_ruleHasNoTenantDimension() {
        ShardProperties timeRotated = ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("monitor_metric", ShardMode.MONTH, ShardRouteBy.NONE, 2));
        ShardInnerInterceptor timeGuard = new ShardInnerInterceptor(timeRotated,
                ShardTestFixtures.routerOf(timeRotated));

        BizException refused = catchThrowableOfType(
                () -> timeGuard.guard("com.x.metric", "SELECT * FROM monitor_metric"), BizException.class);

        assertThat(refused.getMessage()).contains("时间值").doesNotContain("当前租户号");
    }

    @Test
    @DisplayName("表名大小写与引号不影响判定：SQL 写 ALERT_EVENT 之外的大小写也认")
    void should_matchCaseInsensitively_when_sqlAndBindingCaseDiffer() {
        try (ShardContext.ShardScope ignored = ShardContext.open("ALERT_EVENT", 903L, "2026-11-06")) {
            guard.guard("com.x.upper", "SELECT id FROM Alert_Event");

            assertThat(ShardContext.current().getResolvedInstanceId()).isEqualTo("ds1");
        }

        assertThatThrownBy(() -> guard.guard("com.x.upper", "SELECT id FROM `alert_event`"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("alert_event");
    }

    // ------------------------------------------------------------------
    // MyBatis 挂点（MappedStatement 是 final 类，mock 不了，所以真建一个最小的语句）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("beforeQuery 用 BoundSql 里的 SQL 走同一段判定")
    void should_guardViaBoundSql_when_beforeQuery() {
        org.apache.ibatis.mapping.MappedStatement ms = statement("com.x.AlertMapper.selectEvents",
                "SELECT id FROM alert_event");

        assertThatThrownBy(() -> guard.beforeQuery(mock(org.apache.ibatis.executor.Executor.class), ms,
                null, org.apache.ibatis.session.RowBounds.DEFAULT, null, ms.getBoundSql(null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("alert_event");
    }

    @Test
    @DisplayName("beforeUpdate 与查询共用同一句话：有绑定就放行并落定状态")
    void should_passThrough_when_beforeUpdateWithContext() {
        org.apache.ibatis.mapping.MappedStatement ms = statement("com.x.AlertMapper.insert",
                "INSERT INTO alert_event (id) VALUES (1)");

        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            guard.beforeUpdate(mock(org.apache.ibatis.executor.Executor.class), ms, new Object());

            assertThat(ShardContext.current().getResolvedInstanceId()).isEqualTo("ds1");
            assertThat(ShardContext.current().getResolvedSuffix()).isEqualTo("_202611");
        }
    }

    @Test
    @DisplayName("动态 SQL 拼不出 boundSql 时跳过本条检查但不静默崩：MyBatis 会在更早的地方失败")
    void should_skipCheck_when_boundSqlCannotBeResolved() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.mapping.SqlSource throwing = new org.apache.ibatis.mapping.SqlSource() {
            @Override
            public org.apache.ibatis.mapping.BoundSql getBoundSql(Object parameterObject) {
                throw new IllegalStateException("动态 SQL 拼不动");
            }
        };
        org.apache.ibatis.mapping.MappedStatement ms = new org.apache.ibatis.mapping.MappedStatement.Builder(
                configuration, "com.x.AlertMapper.broken", throwing,
                org.apache.ibatis.mapping.SqlCommandType.UPDATE).build();

        assertThatCode(() -> guard.beforeUpdate(mock(org.apache.ibatis.executor.Executor.class), ms, new Object()))
                .doesNotThrowAnyException();
    }

    private static org.apache.ibatis.mapping.MappedStatement statement(String id, String sql) {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        return new org.apache.ibatis.mapping.MappedStatement.Builder(configuration, id,
                new org.apache.ibatis.builder.StaticSqlSource(configuration, sql),
                org.apache.ibatis.mapping.SqlCommandType.SELECT).build();
    }

    // ------------------------------------------------------------------
    // 解析口径（闸门与改写器共用同一个 TableNameParser）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("shardedTables：去重、认 FROM/JOIN/INTO/UPDATE 后面的词，子查询两张表都要认出来")
    void should_findShardedTables_when_sqlShapesDiffer() {
        // 顺序不作承诺：MP 的 TableNameParser 对嵌套 FROM 的产出顺序与书写顺序无关，判据是"两张都认出来"
        assertThat(ShardInnerInterceptor.shardedTables(
                "SELECT * FROM alert_event WHERE id IN (SELECT event_id FROM alert_notify_log)", properties))
                .containsExactlyInAnyOrder("alert_event", "alert_notify_log");

        assertThat(ShardInnerInterceptor.shardedTables(
                "INSERT INTO `alert_event` (id) VALUES (1)", properties)).containsExactly("alert_event");
        assertThat(ShardInnerInterceptor.shardedTables(
                "UPDATE alert_event SET status = 1", properties)).containsExactly("alert_event");
        assertThat(ShardInnerInterceptor.shardedTables(
                "DELETE FROM alert_event WHERE id = 1", properties)).containsExactly("alert_event");
        // 同一张表出现两次只算一次（否则自连接会被误判成跨分片 join）
        assertThat(ShardInnerInterceptor.shardedTables(
                "SELECT a.id FROM alert_event a JOIN alert_event b ON a.id = b.parent_id", properties))
                .containsExactly("alert_event");
        assertThat(ShardInnerInterceptor.shardedTables("SELECT 1", properties)).isEmpty();
    }

    @Test
    @DisplayName("distinctLowerTables：给数据源/扇出层复用的同一份表名口径")
    void should_collectDistinctLowerTables_when_namesAreQuotedOrMixedCase() {
        assertThat(ShardInnerInterceptor.distinctLowerTables(
                "SELECT * FROM `Alert_Event` e JOIN sys_tenant t ON t.id = e.tenant_id"))
                .containsExactlyInAnyOrder("alert_event", "sys_tenant");
    }
}
