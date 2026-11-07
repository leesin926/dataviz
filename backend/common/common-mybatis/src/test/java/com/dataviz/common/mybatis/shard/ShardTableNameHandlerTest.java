package com.dataviz.common.mybatis.shard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表名改写器的判据：只改"该改的"，其余<b>原样返回</b>。
 * <p>
 * 原样返回这件事也要钉：改写器一旦顺手改了非分片表或改了大小写，症状是配置表突然查不到、
 * 或者 MySQL 在大小写敏感的文件系统上报表不存在。
 */
class ShardTableNameHandlerTest {

    private final ShardRouter router = ShardTestFixtures.router("alert_event");
    private final ShardTableNameHandler handler = new ShardTableNameHandler(router);

    @AfterEach
    void clearContext() {
        ShardContext.clear();
    }

    @Test
    @DisplayName("绑定了上下文：逻辑名换成月表物理名")
    void should_rewriteToPhysicalName_when_bindingMatches() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(handler.dynamicTableName("SELECT * FROM alert_event", "alert_event"))
                    .isEqualTo("alert_event_202611");
        }
    }

    @Test
    @DisplayName("SQL 里带反引号：改写后不带引号（MP 自己会补，双重引号会让表名变成字面量）")
    void should_rewriteToBareName_when_inputIsQuoted() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(handler.dynamicTableName("SELECT * FROM `alert_event`", "`alert_event`"))
                    .isEqualTo("alert_event_202611");
        }
    }

    @Test
    @DisplayName("非分片表原样返回，连大小写和引号都不动")
    void should_returnInputUnchanged_when_tableIsNotSharded() {
        assertThat(handler.dynamicTableName("SELECT * FROM Sys_Tenant", "`Sys_Tenant`")).isEqualTo("`Sys_Tenant`");
        assertThat(handler.dynamicTableName("SELECT * FROM sys_tenant", "sys_tenant")).isEqualTo("sys_tenant");
        assertThat(handler.dynamicTableName("SELECT * FROM alert_event_archive", "alert_event_archive"))
                .isEqualTo("alert_event_archive");
    }

    @Test
    @DisplayName("分片表但没绑上下文：原样放行，把拒绝留给闸门与数据源（这里不抛第二个错）")
    void should_returnLogicalName_when_shardedButUnbound() {
        assertThat(handler.dynamicTableName("SELECT * FROM alert_event", "alert_event")).isEqualTo("alert_event");
    }

    @Test
    @DisplayName("上下文绑的是别的表：这张表按逻辑名放行（不拿别人的后缀建表）")
    void should_returnLogicalName_when_bindingIsForAnotherTable() {
        try (ShardContext.ShardScope ignored = ShardContext.open("openapi_log", 903L, "2026-11-06")) {
            assertThat(handler.dynamicTableName("SELECT * FROM alert_event", "alert_event")).isEqualTo("alert_event");
        }
    }

    @Test
    @DisplayName("mode=NONE 的分片表：物理名就是逻辑名（只分库不分表）")
    void should_keepLogicalName_when_modeIsNone() {
        ShardProperties properties = ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("monitor_metric", ShardMode.NONE, ShardRouteBy.TENANT, 2));
        ShardTableNameHandler noneHandler = new ShardTableNameHandler(ShardTestFixtures.routerOf(properties));

        try (ShardContext.ShardScope ignored = ShardContext.open("monitor_metric", 903L, "2026-11-06")) {
            assertThat(noneHandler.dynamicTableName("SELECT * FROM monitor_metric", "monitor_metric"))
                    .isEqualTo("monitor_metric");
        }
    }

    @Test
    @DisplayName("按日分表的物理名带 8 位日期")
    void should_appendEightDigitSuffix_when_modeDay() {
        ShardProperties properties = ShardTestFixtures.properties(2,
                ShardTestFixtures.rule("sys_login_log", ShardMode.DAY, ShardRouteBy.TENANT, 2));
        ShardTableNameHandler dayHandler = new ShardTableNameHandler(ShardTestFixtures.routerOf(properties));

        try (ShardContext.ShardScope ignored = ShardContext.open("sys_login_log", 903L, "2026-11-06 10:15:30")) {
            assertThat(dayHandler.dynamicTableName("SELECT 1", "sys_login_log")).isEqualTo("sys_login_log_20261106");
        }
    }

    @Test
    @DisplayName("null 表名返回 null，不抛空指针（MP 传进来的东西不该由这层来兜异常）")
    void should_returnNull_when_tableNameIsNull() {
        assertThat(handler.dynamicTableName("SELECT 1", null)).isNull();
    }

    // ------------------------------------------------------------------
    // unquote
    // ------------------------------------------------------------------

    @Test
    @DisplayName("unquote：三种引号外壳都脱，配错的壳保持原样，空串给 null")
    void should_stripMatchingQuotesOnly_when_unquoteRuns() {
        assertThat(ShardTableNameHandler.unquote("`alert_event`")).isEqualTo("alert_event");
        assertThat(ShardTableNameHandler.unquote("\"alert_event\"")).isEqualTo("alert_event");
        assertThat(ShardTableNameHandler.unquote("[alert_event]")).isEqualTo("alert_event");
        assertThat(ShardTableNameHandler.unquote("  `alert_event`  ")).isEqualTo("alert_event");
        assertThat(ShardTableNameHandler.unquote("alert_event")).isEqualTo("alert_event");
        // 只有一边有引号的是写坏的 SQL，不能当成"表名带引号"
        assertThat(ShardTableNameHandler.unquote("`alert_event")).isEqualTo("`alert_event");
        assertThat(ShardTableNameHandler.unquote("db_openapi.openapi_log")).isEqualTo("db_openapi.openapi_log");
        assertThat(ShardTableNameHandler.unquote(null)).isNull();
        assertThat(ShardTableNameHandler.unquote("")).isNull();
        assertThat(ShardTableNameHandler.unquote("`")).isEqualTo("`");
    }

    @Test
    @DisplayName("库名前缀不参与分片：db.alert_event 认成一张叫 db.alert_event 的表，改写不发生在它身上")
    void should_notRewrite_when_tableNameCarriesSchemaPrefix() {
        // 这是一条"要知道的边界"：Mapper XML 里写死库名前缀会让分片静默失效，闸门看不到分片表名所以也不拒绝。
        // 现在的守卫是 Mapper 里不许写库名（本仓库的 mapper 全部走单库数据源），改这条前先想清楚。
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(handler.dynamicTableName("SELECT * FROM db.alert_event", "db.alert_event"))
                    .isEqualTo("db.alert_event");
        }

        assertThatThrownBy(() -> ShardRouter.physical("db.alert_event", "_202611"))
                .hasMessageContaining("逻辑表名不合法");
    }
}
