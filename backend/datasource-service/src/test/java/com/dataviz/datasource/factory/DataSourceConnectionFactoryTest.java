package com.dataviz.datasource.factory;

import com.dataviz.datasource.test.MybatisPlusTableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DataSourceConnectionFactory} 的驱动/URL/口令解析与 HikariCP 连接池的建/复用/回收。
 * <p>
 * 全程不建真实 JDBC 连接：URL 拼装走私有方法反射断言精确字符串；连接池用反射注入的 mock
 * {@link HikariDataSource} 驱动 computeIfAbsent 复用与 close 语义，绝不触碰 DriverManager。
 */
class DataSourceConnectionFactoryTest {

    private DataSourceConnectionFactory factory;
    private Map<Long, HikariDataSource> poolMap;

    @BeforeAll
    static void initTableInfo() {
        // buildJdbcUrl 对未知/非 JDBC 类型会经 DatasourceType.fromCode，与实体无关，这里仅为统一初始化上下文
        MybatisPlusTableInfoHelper.initFor(com.dataviz.datasource.entity.Datasource.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        factory = new DataSourceConnectionFactory(new ObjectMapper());
        Field field = DataSourceConnectionFactory.class.getDeclaredField("poolMap");
        field.setAccessible(true);
        //noinspection unchecked
        poolMap = (Map<Long, HikariDataSource>) field.get(factory);
    }

    // ---- buildJdbcUrl（反射调用私有方法，断言精确串）------------------------

    private String buildUrl(String type, String config) throws Exception {
        Method method = DataSourceConnectionFactory.class
                .getDeclaredMethod("buildJdbcUrl", String.class, String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(factory, type, config);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    @DisplayName("buildJdbcUrl - MySQL 拼出带完整参数尾的连接串（含时区与公钥检索）")
    void should_buildMySqlUrlWithParameterTail_when_mysql() throws Exception {
        String url = buildUrl("mysql", "{\"host\":\"10.0.0.9\",\"port\":3307,\"database\":\"dw\"}");

        assertThat(url).isEqualTo(
                "jdbc:mysql://10.0.0.9:3307/dw?useUnicode=true&characterEncoding=utf8"
                        + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    }

    @Test
    @DisplayName("buildJdbcUrl - PostgreSQL 用默认端口缺省 3306 且无参数尾")
    void should_buildPostgresUrl_when_postgresql() throws Exception {
        String url = buildUrl("postgresql", "{\"host\":\"pg.local\",\"port\":5432,\"database\":\"dw\"}");

        assertThat(url).isEqualTo("jdbc:postgresql://pg.local:5432/dw");
    }

    @Test
    @DisplayName("buildJdbcUrl - ClickHouse/Oracle/SQLServer 各自的专有形状")
    void should_buildVendorSpecificUrl_when_clickhouseOracleSqlserver() throws Exception {
        assertThat(buildUrl("clickhouse", "{\"host\":\"ch\",\"port\":8123,\"database\":\"dw\"}"))
                .isEqualTo("jdbc:clickhouse://ch:8123/dw");
        assertThat(buildUrl("oracle", "{\"host\":\"ora\",\"port\":1521,\"database\":\"ORCL\"}"))
                .isEqualTo("jdbc:oracle:thin:@ora:1521:ORCL");
        assertThat(buildUrl("sqlserver", "{\"host\":\"mssql\",\"port\":1433,\"database\":\"dw\"}"))
                .isEqualTo("jdbc:sqlserver://mssql:1433;databaseName=dw");
    }

    @Test
    @DisplayName("buildJdbcUrl - host/port/database 缺失时套用 localhost/3306/空库名缺省")
    void should_applyDefaults_when_hostPortDatabaseAbsent() throws Exception {
        String url = buildUrl("postgresql", "{}");

        // database 缺省为 ""，URL 以斜杠结尾
        assertThat(url).isEqualTo("jdbc:postgresql://localhost:3306/");
    }

    @Test
    @DisplayName("buildJdbcUrl - 未知类型码经 fromCode 抛 IllegalArgumentException")
    void should_throwIllegalArgument_when_unknownTypeCode() {
        assertThatThrownBy(() -> buildUrl("db2", "{\"host\":\"h\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown datasource type: db2");
    }

    @Test
    @DisplayName("buildJdbcUrl - 非 JDBC 类型（elasticsearch）走 switch default 明确拒绝")
    void should_throwUnsupported_when_nonJdbcType() {
        assertThatThrownBy(() -> buildUrl("elasticsearch", "{\"host\":\"h\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported JDBC datasource type: elasticsearch");
    }

    // ---- getTestConnection（一次性，不入池）--------------------------------

    @Test
    @DisplayName("getTestConnection - 非法 config JSON 时包成 RuntimeException 并保留构建失败语义")
    void should_wrapAsRuntime_when_testConnectionConfigMalformed() {
        assertThatThrownBy(() -> factory.getTestConnection("mysql", "{not-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create test connection");
    }

    // ---- 连接池 computeIfAbsent 复用 / removePool / destroy ---------------

    @Test
    @DisplayName("getConnection - 同一 id 复用已存在的池，不重复建池也不关闭")
    void should_reuseExistingPool_when_sameDatasourceIdRequested() throws Exception {
        HikariDataSource existing = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        when(existing.getConnection()).thenReturn(conn);
        poolMap.put(1L, existing);

        Connection first = factory.getConnection(1L, "mysql", "{\"host\":\"h\"}");
        Connection second = factory.getConnection(1L, "mysql", "{\"host\":\"h\"}");

        assertThat(first).isSameAs(conn);
        assertThat(second).isSameAs(conn);
        // 两次都命中现成池：getConnection 各调一次，池没被重建也没被关闭
        verify(existing, times(2)).getConnection();
        verify(existing, never()).close();
        assertThat(poolMap).hasSize(1).containsKey(1L);
    }

    @Test
    @DisplayName("removePool - 移除并关闭一次；对不存在的 id 静默无操作")
    void should_closeAndRemoveOnce_when_removePool() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.isClosed()).thenReturn(false);
        poolMap.put(2L, ds);

        factory.removePool(2L);

        verify(ds, times(1)).close();
        assertThat(poolMap).doesNotContainKey(2L);

        // 不存在的 id 不抛异常、不关任何池
        factory.removePool(404L);
        verify(ds, times(1)).close();
    }

    @Test
    @DisplayName("destroy - 关闭所有未关闭的池且跳过已关闭的，最后清空映射")
    void should_closeAllUnclosedAndClear_when_destroy() {
        HikariDataSource open = mock(HikariDataSource.class);
        when(open.isClosed()).thenReturn(false);
        HikariDataSource alreadyClosed = mock(HikariDataSource.class);
        when(alreadyClosed.isClosed()).thenReturn(true);
        poolMap.put(10L, open);
        poolMap.put(20L, alreadyClosed);

        factory.destroy();

        verify(open, times(1)).close();
        verify(alreadyClosed, never()).close();
        assertThat(poolMap).isEmpty();
    }
}
