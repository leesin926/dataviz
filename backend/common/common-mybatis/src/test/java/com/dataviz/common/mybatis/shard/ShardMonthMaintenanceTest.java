package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 月表预建的判据：计划算得对（跨年）、执行连得对（按实例拿连接）、默认不跑。
 * <p>
 * 最后一件事是仓库级闸门的要求（alert-service 的 {@code AlertCheckEngineRetirementTest} 那条
 * "每个 @Scheduled 要么唯一要么默认关"）：一个克隆下来就改生产库结构的定时器不能默认在跑。
 */
class ShardMonthMaintenanceTest {

    private final ShardProperties properties = ShardTestFixtures.twoInstances(
            ShardTestFixtures.rule("alert_event"),
            ShardTestFixtures.rule("sys_login_log", ShardMode.DAY, ShardRouteBy.TENANT, 2),
            ShardTestFixtures.rule("monitor_metric", ShardMode.NONE, ShardRouteBy.TENANT, 2));
    private final ShardRouter router = ShardTestFixtures.routerOf(properties);
    private final ResourceLoader resourceLoader = mock(ResourceLoader.class);
    private final DataSource dataSource = mock(DataSource.class);

    private ShardMonthMaintenance maintenance() {
        return new ShardMonthMaintenance(properties, router, dataSource, resourceLoader);
    }

    // ------------------------------------------------------------------
    // 计划
    // ------------------------------------------------------------------

    @Test
    @DisplayName("12 月中旬的预建计划跨到次年 1 月：两个月 × 两个实例 = 4 条 DDL")
    void should_crossYearBoundary_when_planningInDecember() {
        List<ShardMonthMaintenance.DdlTask> tasks = maintenance().plan(LocalDate.of(2026, 12, 15));

        assertThat(tasks).hasSize(4);
        assertThat(tasks).extracting(ShardMonthMaintenance.DdlTask::getPhysicalTable)
                .containsExactly("alert_event_202612", "alert_event_202612",
                        "alert_event_202701", "alert_event_202701");
        assertThat(tasks).extracting(ShardMonthMaintenance.DdlTask::getInstanceId)
                .containsExactly("ds0", "ds1", "ds0", "ds1");
        assertThat(tasks).allSatisfy(task ->
                assertThat(task.getDdl()).startsWith("CREATE TABLE IF NOT EXISTS `"));
    }

    @Test
    @DisplayName("只有 MONTH 规则参与月表预建：DAY 表归日巡检，NONE 表没有月表可建")
    void should_planOnlyMonthRules_when_otherModesConfigured() {
        List<ShardMonthMaintenance.DdlTask> tasks = maintenance().plan(LocalDate.of(2026, 11, 6));

        // 当月 + 提前 1 个月 = 2 个月，每月 2 个实例
        assertThat(tasks).extracting(ShardMonthMaintenance.DdlTask::getLogicalTable).containsOnly("alert_event");
        assertThat(tasks).hasSize(4);
    }

    @Test
    @DisplayName("pre-create-months=0 时只补当月")
    void should_planCurrentMonthOnly_when_preCreateMonthsIsZero() {
        ShardProperties single = ShardTestFixtures.twoInstances(
                ShardTestFixtures.withPreCreateMonths(ShardTestFixtures.rule("alert_event"), 0));

        List<ShardMonthMaintenance.DdlTask> tasks =
                new ShardMonthMaintenance(single, ShardTestFixtures.routerOf(single), dataSource, resourceLoader)
                        .plan(LocalDate.of(2026, 11, 6));

        assertThat(tasks).extracting(ShardMonthMaintenance.DdlTask::getSuffix).containsOnly("_202611");
        assertThat(tasks).hasSize(2);
    }

    @Test
    @DisplayName("baseDate 为空直接拒绝：默认值会变成昨天的日期，建错表没人知道")
    void should_reject_when_baseDateMissing() {
        assertThatThrownBy(() -> maintenance().plan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseDate");
    }

    // ------------------------------------------------------------------
    // 模板
    // ------------------------------------------------------------------

    @Test
    @DisplayName("配了模板就用 classpath 上的模板，占位符按物理表/逻辑表/后缀替换")
    void should_readTemplate_when_configured() throws IOException {
        String template = "CREATE TABLE IF NOT EXISTS {PHYSICAL_TABLE} (LIKE {LOGICAL_TABLE}) /* {SUFFIX} */";
        Resource resource = mock(Resource.class);
        when(resource.getInputStream())
                .thenReturn((InputStream) new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8)));
        when(resourceLoader.getResource("classpath:sql/shard/month-table.sql")).thenReturn(resource);
        ShardProperties configured = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
        configured.getPrecreate().setDdlTemplate("classpath:sql/shard/month-table.sql");

        assertThat(new ShardMonthMaintenance(configured, router, dataSource, resourceLoader)
                .templateFor("alert_event")).isEqualTo(template);
        assertThat(ShardMonthMaintenance.render(template, "alert_event", "alert_event_202612", "_202611"))
                .isEqualTo("CREATE TABLE IF NOT EXISTS `alert_event_202612` (LIKE `alert_event`) /* _202611 */");
    }

    @Test
    @DisplayName("没配模板时退化成兜底 LIKE，并且一次资源都不读")
    void should_useFallbackTemplate_when_locationNotConfigured() {
        assertThat(maintenance().templateFor("alert_event")).isEqualTo(ShardMonthMaintenance.FALLBACK_TEMPLATE);
        properties.getPrecreate().setDdlTemplate("   ");
        assertThat(maintenance().templateFor("alert_event")).isEqualTo(ShardMonthMaintenance.FALLBACK_TEMPLATE);

        verifyNoInteractions(resourceLoader);
    }

    @Test
    @DisplayName("模板读不到时点名配置键与位置，但绝不带出口令")
    void should_reportTemplateFailureWithoutCredentials_when_resourceMissing() throws IOException {
        Resource broken = mock(Resource.class);
        when(broken.getInputStream()).thenThrow(new IOException("gone"));
        when(resourceLoader.getResource("classpath:gone.sql")).thenReturn(broken);
        ShardProperties configured = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
        configured.getPrecreate().setDdlTemplate("classpath:gone.sql");
        ShardMonthMaintenance target =
                new ShardMonthMaintenance(configured, router, dataSource, resourceLoader);

        BizException refused = catchThrowableOfType(() -> target.templateFor("alert_event"), BizException.class);

        assertThat(refused.getMessage())
                .contains("shard.precreate.ddl-template=classpath:gone.sql")
                .doesNotContain(ShardTestFixtures.SECRET);
    }

    @Test
    @DisplayName("模板里的占位符替换带反引号；注释与空语句在切分时丢掉")
    void should_splitAndQuote_when_ddlHasComments() {
        List<String> statements = ShardMonthMaintenance.splitStatements(
                "-- 每月一条\nCREATE TABLE `a_202611` LIKE `a`;\n\n-- 尾巴\nCREATE TABLE `a_202612` LIKE `a`;");

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).isEqualTo("CREATE TABLE `a_202611` LIKE `a`");
        assertThat(ShardMonthMaintenance.splitStatements("  ")).isEmpty();
        assertThat(ShardMonthMaintenance.render("SELECT {SUFFIX} FROM {LOGICAL_TABLE}",
                "alert_event", "alert_event_202611", null)).isEqualTo("SELECT  FROM `alert_event`");
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("逻辑表存在时按计划执行 DDL：两个月 × 两个实例 = 4 条")
    void should_executeEveryPlannedTask_when_logicalTableExists() throws Exception {
        Statement statement = mock(Statement.class);
        Connection connection = stubConnection(true, statement);
        when(dataSource.getConnection()).thenReturn(connection);

        int executed = maintenance().execute(LocalDate.of(2026, 11, 6));

        assertThat(executed).isEqualTo(4);
        verify(statement, times(2)).execute("CREATE TABLE IF NOT EXISTS `alert_event_202611` LIKE `alert_event`");
        verify(statement, times(2)).execute("CREATE TABLE IF NOT EXISTS `alert_event_202612` LIKE `alert_event`");
    }

    @Test
    @DisplayName("目标实例缺逻辑表 → 带实例 id 与表名失败，而不是让 MySQL 报半截错")
    void should_refuseWithInstanceName_when_logicalTableMissingOnThatInstance() throws Exception {
        Connection connection = stubConnection(false, mock(Statement.class));
        when(dataSource.getConnection()).thenReturn(connection);

        BizException refused = catchThrowableOfType(
                () -> maintenance().execute(LocalDate.of(2026, 11, 6)), BizException.class);

        assertThat(refused.getMessage())
                .contains("缺逻辑表 alert_event")
                .contains("ds0")
                .doesNotContain(ShardTestFixtures.SECRET);
    }

    @Test
    @DisplayName("配了自己的模板时不再挡缺基表（模板自带结构定义，新库也该能建）")
    void should_skipLogicalTableCheck_when_templateProvided() throws Exception {
        Resource resource = mock(Resource.class);
        when(resource.getInputStream()).thenReturn((InputStream) new ByteArrayInputStream(
                "CREATE TABLE IF NOT EXISTS {PHYSICAL_TABLE} (id bigint)".getBytes(StandardCharsets.UTF_8)));
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        ShardProperties configured = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
        configured.getPrecreate().setDdlTemplate("classpath:sql/shard/month-table.sql");
        Connection connection = stubConnection(false, mock(Statement.class));
        when(dataSource.getConnection()).thenReturn(connection);

        int executed = new ShardMonthMaintenance(configured, router, dataSource, resourceLoader)
                .execute(LocalDate.of(2026, 11, 6));

        assertThat(executed).isEqualTo(4);
        verify(connection, never()).prepareStatement("SHOW TABLES LIKE ?");
    }

    // ------------------------------------------------------------------
    // 门控
    // ------------------------------------------------------------------

    @Test
    @DisplayName("开关默认关：定时入口被调用也不碰数据源")
    void should_doNothing_when_precreateDisabledByDefault() throws Exception {
        ShardMonthMaintenance target = maintenance();

        target.preCreateNextMonthTables();

        assertThat(target.isPrecreateEnabled()).isFalse();
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("@Scheduled 的 cron 走配置键且带默认值，属性名与默认值都写死在注解上（给看门测试静态可见）")
    void should_exposeGateStatically_when_schedulerInspected() throws Exception {
        Scheduled scheduled = ShardMonthMaintenance.class
                .getMethod("preCreateNextMonthTables")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).as("月表预建是定时器，看门测试要求它必须可见地默认关").isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${shard.precreate.cron:0 15 3 * * ?}");
        assertThat(ShardMonthMaintenance.class.getDeclaredField("precreateEnabled")
                .getAnnotation(Value.class).value()).isEqualTo("${shard.precreate.enabled:false}");
    }

    @Test
    @DisplayName("打开开关后定时入口真的会执行计划；异常只记日志不外抛（定时任务抛出去会被线程池吃掉）")
    void should_runPlanAndSwallowFailure_when_gateOpened() throws Exception {
        Statement statement = mock(Statement.class);
        Connection connection = stubConnection(true, statement);
        when(dataSource.getConnection()).thenReturn(connection);
        ShardMonthMaintenance target = maintenance();
        ReflectionTestUtils.setField(target, "precreateEnabled", true);

        target.preCreateNextMonthTables();

        verify(statement, times(4)).execute(anyString());

        ShardMonthMaintenance broken = maintenance();
        ReflectionTestUtils.setField(broken, "precreateEnabled", true);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("库没起"));
        broken.preCreateNextMonthTables();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 造一条假连接：{@code SHOW TABLES} 是否命中由 exists 决定，DDL 交给传入的 Statement。 */
    private Connection stubConnection(boolean logicalTableExists, Statement statement) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(logicalTableExists);
        when(connection.createStatement()).thenReturn(statement);
        return connection;
    }
}
