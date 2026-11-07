package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 连接层路由的判据。
 * <p>
 * 这里最不能出错的两件事：① 未绑定时不许把<b>分片表</b>的语句悄悄送去默认实例（数据写错库）；
 * ② 任何异常与 {@code toString()} 里不许出现连接串或口令（这些会进日志和响应体）。
 * 测试不需要数据库：Hikari 用无参构造 + setter，池在首次 {@code getConnection} 才初始化。
 */
class ShardRoutingDataSourceTest {

    private final ShardProperties properties = ShardTestFixtures.twoInstances(
            ShardTestFixtures.rule("alert_event"),
            ShardTestFixtures.rule("alert_notify_log"));
    private final ShardRouter router = ShardTestFixtures.routerOf(properties);
    private final ShardRoutingDataSource dataSource =
            new ShardRoutingDataSource(properties, router);

    ShardRoutingDataSourceTest() {
        dataSource.afterPropertiesSet();
    }

    @AfterEach
    void clearContext() {
        ShardContext.clear();
    }

    @Test
    @DisplayName("线程上没有绑定 → 默认实例（非分片表本来就没有分片键，不能被这条路挡住）")
    void should_useDefaultInstance_when_contextUnbound() {
        assertThat(dataSource.determineCurrentLookupKey()).isEqualTo("ds0");
    }

    @Test
    @DisplayName("绑定 903 租户的分片表 → 走 ds1，与实测桶位一致")
    void should_routeToSecondInstance_when_tenantIs903() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(dataSource.determineCurrentLookupKey()).isEqualTo("ds1");
        }
        // 出了作用域必须回到默认实例：线程池复用线程时残留绑定 = 下一个请求查到别人的数据
        assertThat(dataSource.determineCurrentLookupKey()).isEqualTo("ds0");
    }

    @Test
    @DisplayName("绑定存在但租户为空 → 拒绝，且不退回默认实例")
    void should_refuse_when_boundButTenantIsBlank() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", null, "2026-11-06")) {
            assertThatThrownBy(() -> dataSource.determineCurrentLookupKey())
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("tenant_id")
                    .hasMessageContaining("为空");
        }
    }

    @Test
    @DisplayName("路由值类型不受支持时，连接层与 SQL 层报同一句话（不退默认实例）")
    void should_refuseWithUnifiedMessage_when_routeValueTypeUnsupported() {
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", new Object(), "2026-11-06")) {
            assertThatThrownBy(() -> dataSource.determineCurrentLookupKey())
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("类型不支持");
        }
    }

    @Test
    @DisplayName("lookupKeyFor：非分片表给默认实例，分片表没有绑定就拒绝")
    void should_answerLookupKeyForTable_when_askedExplicitly() {
        assertThat(dataSource.lookupKeyFor("sys_tenant")).isEqualTo("ds0");

        assertThatThrownBy(() -> dataSource.lookupKeyFor("alert_event"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未绑定路由上下文");

        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(dataSource.lookupKeyFor("alert_event")).isEqualTo("ds1");
        }
        // 绑的是 A 表、问的是 B 表：拒绝，而不是拿 A 的实例去执行 B
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThatThrownBy(() -> dataSource.lookupKeyFor("alert_notify_log"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("alert_notify_log");
        }
    }

    @Test
    @DisplayName("toString 与实例清单只有 id：连接串与口令一个字符都不许出现")
    void should_notLeakCredentials_when_renderedAsText() {
        assertThat(dataSource.toString()).contains("ds0").contains("ds1");
        assertThat(dataSource.toString())
                .doesNotContain(ShardTestFixtures.SECRET)
                .doesNotContain("jdbc:mysql")
                .doesNotContain("root");
        assertThat(dataSource.getInstanceIds()).containsExactly("ds0", "ds1");
    }

    @Test
    @DisplayName("池是懒的：建好数据源不碰库，所以起不来的库不该让 Spring 上下文启动失败")
    void should_notOpenConnections_when_poolsAreBuilt() {
        DataSource raw = ShardRoutingDataSource.createPool(ShardTestFixtures.instance("ds0"));

        assertThat(raw).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) raw).isRunning()).isFalse();
        assertThat(((HikariDataSource) raw).getPoolName()).isEqualTo("shard-ds0");
        assertThat(((HikariDataSource) raw).getMaximumPoolSize()).isEqualTo(10);
        assertThat(((HikariDataSource) raw).getConnectionTimeout()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("池参数按实例配置覆盖默认值（分片池要小：总连接数 = 实例数 × maximum-pool-size）")
    void should_applyPerInstancePoolSettings_when_poolConfigured() {
        ShardProperties.Instance instance = ShardTestFixtures.instance("ds7");
        ShardProperties.Pool pool = new ShardProperties.Pool();
        pool.setMaximumPoolSize(3);
        pool.setMinimumIdle(1);
        pool.setConnectionTimeout(1234L);
        instance.setPool(pool);

        HikariDataSource dataSource = (HikariDataSource) ShardRoutingDataSource.createPool(instance);

        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(3);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("pool 为空时用默认值，不空指针")
    void should_fallBackToDefaults_when_poolNotConfigured() {
        ShardProperties.Instance instance = ShardTestFixtures.instance("ds0");
        instance.setPool(null);

        HikariDataSource dataSource = (HikariDataSource) ShardRoutingDataSource.createPool(instance);

        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("实例配置残缺当场拒绝，报错不提连接串")
    void should_refusePoolCreation_when_instanceIsIncomplete() {
        assertThatThrownBy(() -> ShardRoutingDataSource.createPool(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配置不完整");

        ShardProperties.Instance noId = new ShardProperties.Instance();
        noId.setUrl("jdbc:mysql://127.0.0.1:3306/db_x");
        assertThatThrownBy(() -> ShardRoutingDataSource.createPool(noId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配置不完整");
    }

    @Test
    @DisplayName("构造数据源时 properties/router 缺一就拒绝（不给半装配的对象）")
    void should_refuseConstruction_when_dependenciesMissing() {
        assertThatThrownBy(() -> new ShardRoutingDataSource(null, router))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRoutingDataSource(properties, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("只有一个实例时非分片表也照旧可用（分库数=1 是合法配置）")
    void should_stillAnswerDefault_when_singleInstance() {
        ShardProperties single = ShardTestFixtures.properties(1,
                ShardTestFixtures.rule("alert_event", ShardMode.MONTH, ShardRouteBy.TENANT, 1));
        ShardRoutingDataSource one = new ShardRoutingDataSource(single, ShardTestFixtures.routerOf(single));
        one.afterPropertiesSet();

        assertThat(one.determineCurrentLookupKey()).isEqualTo("ds0");
        try (ShardContext.ShardScope ignored = ShardContext.open("alert_event", 903L, "2026-11-06")) {
            assertThat(one.determineCurrentLookupKey()).isEqualTo("ds0");
        }
    }

    @Test
    @DisplayName("实例清单里有 null 元素时 instanceIds 跳过它（配置绑坏的兜底）")
    void should_skipNullInstances_when_listingIds() {
        ShardProperties broken = new ShardProperties();
        broken.setInstances(Arrays.asList(ShardTestFixtures.instance("ds0"), null));

        assertThat(broken.instanceIds()).containsExactly("ds0");
        assertThat(broken.defaultInstanceId()).isEqualTo("ds0");
    }
}
