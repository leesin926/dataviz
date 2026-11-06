package com.dataviz.datasource.service.impl;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.connector.ConnectorCapability;
import com.dataviz.datasource.connector.DatasourceConnector;
import com.dataviz.datasource.connector.MongoConnector;
import com.dataviz.datasource.connector.TcpProbe;
import com.dataviz.datasource.dto.DatasourceCreateDTO;
import com.dataviz.datasource.dto.QueryExecuteDTO;
import com.dataviz.datasource.dto.WriteBatchDTO;
import com.dataviz.datasource.entity.Datasource;
import com.dataviz.datasource.entity.DatasourceType;
import com.dataviz.datasource.factory.DataSourceConnectionFactory;
import com.dataviz.datasource.mapper.DatasourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 连接器路由：七个原「非JDBC」拒绝点改按能力判定之后的分流决策。
 * <p>
 * 判据是"谁被调用、谁没被调用"：JDBC 四件套（含 mysql）必须仍逐字节走
 * {@link DataSourceConnectionFactory}；clickhouse/elasticsearch 走 HTTP 连接器；
 * mongodb 走"仅可达性"连接器并在列表/查询时给出缺驱动 jar 的真实拒绝原因；
 * api/csv/excel 维持原文案。ClickHouse 虽然枚举里还挂着 JDBC 驱动名，也应当优先连接器 ——
 * 离线产物里没有 clickhouse-jdbc jar，走 JDBC 只会得到"驱动类找不到"。
 */
class DatasourceServiceImplConnectorRoutingTest {

    private static final long DS_ID = 7L;
    private static final String CK_CONFIG = "{\"host\":\"ch\",\"port\":8123,\"database\":\"dataviz\"}";
    private static final String ES_CONFIG = "{\"host\":\"es\",\"port\":9200}";
    private static final String MONGO_CONFIG = "{\"host\":\"mongo\",\"port\":27017,"
            + "\"username\":\"admin\",\"password\":\"admin123456\"}";

    private DatasourceMapper datasourceMapper;
    private DataSourceConnectionFactory connectionFactory;
    private DatasourceConnector clickHouseConnector;
    private DatasourceConnector elasticsearchConnector;
    private TcpProbe tcpProbe;
    private MongoConnector mongoConnector;
    private DatasourceServiceImpl datasourceService;

    @BeforeEach
    void setUp() {
        datasourceMapper = mock(DatasourceMapper.class);
        connectionFactory = mock(DataSourceConnectionFactory.class);
        clickHouseConnector = mock(DatasourceConnector.class);
        elasticsearchConnector = mock(DatasourceConnector.class);
        tcpProbe = mock(TcpProbe.class);
        // Mongo 用真实连接器：路由测的就是它那份「缺驱动 jar」的拒绝文案能否原样透到调用方
        mongoConnector = new MongoConnector(new ObjectMapper(), tcpProbe);

        lenient().when(clickHouseConnector.supports(any(DatasourceType.class)))
                .thenAnswer(inv -> inv.getArgument(0) == DatasourceType.CLICKHOUSE);
        lenient().when(elasticsearchConnector.supports(any(DatasourceType.class)))
                .thenAnswer(inv -> inv.getArgument(0) == DatasourceType.ELASTICSEARCH);
        lenient().when(clickHouseConnector.capability()).thenReturn(ConnectorCapability.dataAccess());
        lenient().when(elasticsearchConnector.capability()).thenReturn(ConnectorCapability.dataAccess());

        datasourceService = new DatasourceServiceImpl(datasourceMapper, connectionFactory,
                Arrays.asList(clickHouseConnector, elasticsearchConnector, mongoConnector));
    }

    // ---- helpers -----------------------------------------------------------

    private Datasource entity(String type, String config) {
        Datasource ds = new Datasource();
        ds.setId(DS_ID);
        ds.setType(type);
        ds.setConfig(config);
        ds.setTenantId("T-1");
        ds.setStatus(1);
        return ds;
    }

    private QueryExecuteDTO queryDto(String sql) {
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql(sql);
        return dto;
    }

    /** 0 行的 JDBC 结果集桩：只用来证明"路由到了工厂"，行数内容由连接器用例负责。 */
    private Connection emptyJdbcConnection() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        lenient().when(conn.createStatement()).thenReturn(stmt);
        lenient().when(stmt.executeQuery(anyString())).thenReturn(rs);
        lenient().when(rs.getMetaData()).thenReturn(meta);
        lenient().when(meta.getColumnCount()).thenReturn(0);
        lenient().when(rs.next()).thenReturn(false);
        return conn;
    }

    private DatasourceQueryResult sampleConnectorResult() {
        DatasourceQueryResult result = new DatasourceQueryResult();
        result.setColumns(Collections.singletonList("ok"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ok", 1L);
        result.setRows(Collections.singletonList(row));
        result.setRowCount(1);
        result.setTruncated(false);
        result.setExecutionTime(12L);
        result.setSql("SELECT 1");
        return result;
    }

    // =======================================================================
    // executeQuery 分流
    // =======================================================================

    @Test
    @DisplayName("查询路由 - mysql/postgresql/oracle/sqlserver 仍进 DataSourceConnectionFactory，连接器不被触碰")
    void should_routeToJdbcFactory_when_typeIsJdbcFour() throws Exception {
        for (String type : Arrays.asList("mysql", "postgresql", "oracle", "sqlserver")) {
            // Given
            Connection conn = emptyJdbcConnection();
            when(datasourceMapper.selectById(DS_ID)).thenReturn(entity(type, CK_CONFIG));
            when(connectionFactory.getConnection(eq(DS_ID), eq(type), anyString())).thenReturn(conn);

            // When
            Map<String, Object> result = datasourceService.executeQuery(queryDto("SELECT 1"));

            // Then
            assertThat(result.get("rowCount")).isEqualTo(0);
            verify(connectionFactory).getConnection(DS_ID, type, CK_CONFIG);
        }
        // 连接器只被 supports() 路过一次，任何数据方法都不该被 JDBC 类型触发
        verify(clickHouseConnector, never()).query(anyString(), anyString(), anyInt(), anyInt());
        verify(clickHouseConnector, never()).testConnection(anyString());
        verify(elasticsearchConnector, never()).query(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("查询路由 - clickhouse 走 HTTP 连接器，maxRows/timeout 按 DTO 缺省 1000/30 透传")
    void should_routeClickHouseToConnector_when_executeQuery() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));
        when(clickHouseConnector.query(CK_CONFIG, "SELECT 1", 1000, 30)).thenReturn(sampleConnectorResult());

        // When
        Map<String, Object> result = datasourceService.executeQuery(queryDto("SELECT 1"));

        // Then: 复用内部查询端点既有响应形状，不另造 key
        assertThat(result.get("columns")).isEqualTo(Collections.singletonList("ok"));
        assertThat((List<?>) result.get("rows")).hasSize(1);
        assertThat(result.get("rowCount")).isEqualTo(1);
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat(result.get("executionTime")).isEqualTo(12L);
        assertThat(result.get("sql")).isEqualTo("SELECT 1");
        // JDBC 路径一步都不能被碰到（离线没有 clickhouse-jdbc jar）
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("查询路由 - elasticsearch 走 HTTP 连接器，DTO 显式给的 maxRows/timeout 原样传")
    void should_routeElasticsearchToConnector_when_executeQuery() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("elasticsearch", ES_CONFIG));
        when(elasticsearchConnector.query(ES_CONFIG, "SELECT one FROM \"logs\"", 500, 10))
                .thenReturn(sampleConnectorResult());
        QueryExecuteDTO dto = queryDto("SELECT one FROM \"logs\"");
        dto.setMaxRows(500);
        dto.setTimeout(10);

        // When
        datasourceService.executeQuery(dto);

        // Then
        verify(elasticsearchConnector).query(ES_CONFIG, "SELECT one FROM \"logs\"", 500, 10);
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("查询路由 - mongodb 被真实 MongoConnector 拒绝：400 点名缺 mongodb-driver-sync，不建 JDBC 连接")
    void should_refuseMongoQueryWithDriverReason_when_executeQuery() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", MONGO_CONFIG));

        // When / Then
        assertThatThrownBy(() -> datasourceService.executeQuery(queryDto("SELECT 1")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("MongoDB 不支持查询")
                .hasMessageContaining("mongodb-driver-sync")
                .hasMessageNotContaining("admin123456");

        verifyNoInteractions(connectionFactory);
        verifyNoInteractions(tcpProbe);
    }

    /**
     * 实跑（BH 连接器探针）发现的顺序问题：提交 MongoDB shell 语句时，先撞上的是只读白名单，
     * 回的是"只允许执行只读查询语句 (SELECT/SHOW/DESCRIBE)"。照做换成 SELECT 依然取不到 —— 
     * 真正的原因是离线没有驱动 jar。类型本身取不了数时，必须先说这个原因。
     */
    @Test
    @DisplayName("查询路由 - mongodb 传 shell 语句也先给驱动原因，不让只读文案把人引去改 SQL")
    void should_refuseWithDriverReason_when_mongoShellSyntaxSubmitted() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", MONGO_CONFIG));

        // When / Then
        assertThatThrownBy(() -> datasourceService.executeQuery(queryDto("db.demo_orders.find().limit(5)")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("MongoDB 不支持查询")
                .hasMessageNotContaining("只允许执行只读查询语句")
                .hasMessageNotContaining("admin123456");

        verifyNoInteractions(connectionFactory);
        verifyNoInteractions(tcpProbe);
    }

    @Test
    @DisplayName("查询路由 - api 类型（无连接器且非 JDBC）维持原拒绝文案")
    void should_keepLegacyRefusal_when_executeQueryApiType() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("api", "{\"url\":\"http://x\"}"));

        // When / Then
        assertThatThrownBy(() -> datasourceService.executeQuery(queryDto("SELECT 1")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源不支持SQL查询");

        verifyNoInteractions(connectionFactory);
        verify(clickHouseConnector, never()).query(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("查询路由 - 连接器路径同样受只读白名单约束：DELETE 到不了连接器")
    void should_rejectNonReadSql_when_typeHasConnector() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));

        // When / Then
        assertThatThrownBy(() -> datasourceService.executeQuery(queryDto("DELETE FROM orders")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只允许执行只读查询语句 (SELECT/SHOW/DESCRIBE)");

        verify(clickHouseConnector, never()).query(anyString(), anyString(), anyInt(), anyInt());
    }

    // =======================================================================
    // testConnection / getTables / 列与写入 分流
    // =======================================================================

    @Test
    @DisplayName("测连接路由 - clickhouse 调连接器探测，不再抛「非JDBC不支持连接测试」也不建 JDBC 连接")
    void should_routeTestConnectionToConnector_when_clickHouseById() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));
        when(clickHouseConnector.testConnection(CK_CONFIG)).thenReturn(true);

        // When / Then
        assertThat(datasourceService.testConnection(DS_ID)).isTrue();
        verify(clickHouseConnector).testConnection(CK_CONFIG);
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("测连接路由 - mongodb 走真实连接器的 TCP 可达性：端口通即 true，全程无 HTTP/JDBC")
    void should_probeTcpReachability_when_testConnectionMongo() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", MONGO_CONFIG));
        when(tcpProbe.reachable("mongo", 27017, 2000)).thenReturn(true);

        // When / Then
        assertThat(datasourceService.testConnection(DS_ID)).isTrue();
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("创建路由 - clickhouse 创建时也先经连接器验连通，验不过则不落库")
    void should_probeConnectorBeforePersist_when_createElasticsearch() {
        // Given
        when(clickHouseConnector.testConnection(CK_CONFIG)).thenReturn(false);
        DatasourceCreateDTO create = new DatasourceCreateDTO();
        create.setName("ch-库");
        create.setType("clickhouse");
        create.setConfig(CK_CONFIG);

        // When / Then
        assertThatThrownBy(() -> datasourceService.createDatasource(create, "T-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法建立有效连接");

        verify(datasourceMapper, never()).insert(any(Datasource.class));
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("取表路由 - elasticsearch 走连接器并映射 TableInfoVO.tableName，不建 JDBC 连接")
    void should_routeGetTablesToConnector_when_elasticsearch() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("elasticsearch", ES_CONFIG));
        when(elasticsearchConnector.listTables(ES_CONFIG)).thenReturn(Arrays.asList("logs-2024.11", "metrics"));

        // When
        List<com.dataviz.datasource.vo.TableInfoVO> tables = datasourceService.getTables(DS_ID);

        // Then
        assertThat(tables).hasSize(2);
        assertThat(tables.get(0).getTableName()).isEqualTo("logs-2024.11");
        assertThat(tables.get(1).getTableName()).isEqualTo("metrics");
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("取表路由 - mongodb 由真实连接器抛缺驱动 jar 的 400，而不是空列表冒充「没有表」")
    void should_refuseMongoGetTablesWithDriverReason_when_listRequested() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", MONGO_CONFIG));

        // When / Then
        assertThatThrownBy(() -> datasourceService.getTables(DS_ID))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("MongoDB 不支持获取集合列表")
                .hasMessageContaining("mongodb-driver-sync");

        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("取列路由 - 连接器类型列信息不可用，400 里给出可操作替代（DESCRIBE / SELECT 探测）")
    void should_rejectColumnsWithActionableHint_when_typeHasConnector() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));

        // When / Then
        assertThatThrownBy(() -> datasourceService.getTableColumns(DS_ID, "orders"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("DESCRIBE TABLE");

        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("写入路由 - clickhouse 有连接器也不提供写入：400 说明连接器只读，拒于建连之前")
    void should_refuseWriteBatch_when_clickHouseHasReadOnlyConnector() {
        // Given
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));
        WriteBatchDTO dto = new WriteBatchDTO();
        dto.setDatasourceId(DS_ID);
        dto.setTable("orders");
        dto.setColumns(Arrays.asList("id"));
        dto.setRows(Collections.singletonList(Arrays.<Object>asList(1)));

        // When / Then
        assertThatThrownBy(() -> datasourceService.writeBatch(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("不提供写入通道");

        verifyNoInteractions(connectionFactory);
        verify(clickHouseConnector, never()).query(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("路由次序 - clickhouse 枚举里挂着 JDBC 驱动名也优先连接器（离线没有 clickhouse-jdbc jar）")
    void should_preferConnectorOverJdbc_when_clickHouseStillDeclaresDriver() {
        // Given：枚举的 isJdbcSupported()==true 是历史声明，路由必须先看连接器
        assertThat(DatasourceType.CLICKHOUSE.isJdbcSupported()).isTrue();
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("clickhouse", CK_CONFIG));
        when(clickHouseConnector.query(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(sampleConnectorResult());

        // When
        datasourceService.executeQuery(queryDto("SELECT 1"));

        // Then
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(clickHouseConnector).query(eq(CK_CONFIG), sql.capture(), anyInt(), anyInt());
        assertThat(sql.getValue()).isEqualTo("SELECT 1");
        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }
}
