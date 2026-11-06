package com.dataviz.datasource.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.datasource.dto.DatasourceCreateDTO;
import com.dataviz.datasource.dto.DatasourceUpdateDTO;
import com.dataviz.datasource.dto.QueryExecuteDTO;
import com.dataviz.datasource.dto.TestConnectionDTO;
import com.dataviz.datasource.entity.Datasource;
import com.dataviz.datasource.factory.DataSourceConnectionFactory;
import com.dataviz.datasource.mapper.DatasourceMapper;
import com.dataviz.datasource.test.MybatisPlusTableInfoHelper;
import com.dataviz.datasource.vo.ColumnInfoVO;
import com.dataviz.datasource.vo.DatasourceListVO;
import com.dataviz.datasource.vo.DatasourceVO;
import com.dataviz.datasource.vo.TableInfoVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DatasourceServiceImpl} 的边界行为覆盖（writeBatch 单列在
 * {@code DatasourceServiceImplWriteBatchTest}，此处不重复）。
 * <p>
 * 这个服务存放的是"其它系统的凭据"，并据此对外建 JDBC 连接，所以每一处
 * "非 JDBC 类型必须拒绝、口令不得外泄、config 不得被静默清空"都是真实契约，
 * 断言的是实际行为：绿色用例锁现状，红色即缺陷的会以文档形式列出而非改产线。
 */
class DatasourceServiceImplTest {

    private DatasourceMapper datasourceMapper;
    private DataSourceConnectionFactory connectionFactory;
    private DatasourceServiceImpl datasourceService;

    private static final long DS_ID = 42L;
    private static final String TENANT = "T-100";
    /** 一个足够独特、一旦出现在返回体/异常文案里就能被断言捕获的口令。 */
    private static final String SECRET = "Sup3r-S3cret-Pwd";

    @BeforeAll
    static void initTableInfo() {
        // listDatasources 会真正解析 LambdaQueryWrapper 的方法引用，不初始化会抛 lambda cache 缺失
        MybatisPlusTableInfoHelper.initFor(Datasource.class);
    }

    @BeforeEach
    void setUp() {
        datasourceMapper = mock(DatasourceMapper.class);
        connectionFactory = mock(DataSourceConnectionFactory.class);
        datasourceService = new DatasourceServiceImpl(datasourceMapper, connectionFactory);
    }

    // ---- helpers -----------------------------------------------------------

    private Datasource entity(String type, String config) {
        Datasource ds = new Datasource();
        ds.setId(DS_ID);
        ds.setName("生产库");
        ds.setType(type);
        ds.setConfig(config);
        ds.setTenantId(TENANT);
        ds.setStatus(1);
        ds.setDescription("desc");
        ds.setCreateTime(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        return ds;
    }

    private String jdbcConfig() {
        return "{\"host\":\"10.0.0.9\",\"port\":3306,\"database\":\"dw\","
                + "\"username\":\"root\",\"password\":\"" + SECRET + "\"}";
    }

    private DatasourceCreateDTO createDto(String type, String config) {
        DatasourceCreateDTO dto = new DatasourceCreateDTO();
        dto.setName("生产库");
        dto.setType(type);
        dto.setConfig(config);
        dto.setDescription("desc");
        return dto;
    }

    // =======================================================================
    // createDatasource — guard site :49 + config 透传 + 类型校验
    // =======================================================================

    @Test
    @DisplayName("创建 - JDBC 类型先测连接通过后按原样存 config 并置 status=1、返回主键")
    void should_persistConfigVerbatimAndReturnId_when_jdbcTypeConnectionValid() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.isValid(5)).thenReturn(true);
        String config = jdbcConfig();
        when(connectionFactory.getTestConnection("mysql", config)).thenReturn(conn);
        when(datasourceMapper.insert(any(Datasource.class))).thenAnswer(inv -> {
            ((Datasource) inv.getArgument(0)).setId(DS_ID);
            return 1;
        });

        Long id = datasourceService.createDatasource(createDto("mysql", config), TENANT);

        assertThat(id).isEqualTo(DS_ID);
        ArgumentCaptor<Datasource> captor = ArgumentCaptor.forClass(Datasource.class);
        verify(datasourceMapper).insert(captor.capture());
        Datasource saved = captor.getValue();
        assertThat(saved.getConfig()).isEqualTo(config);
        assertThat(saved.getType()).isEqualTo("mysql");
        assertThat(saved.getName()).isEqualTo("生产库");
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("创建 - 非 JDBC 类型跳过连接测试直接落库，工厂一次都不被调用（guard :49）")
    void should_skipConnectionTest_when_nonJdbcTypeOnCreate() {
        String config = "{\"url\":\"http://x\",\"password\":\"" + SECRET + "\"}";
        when(datasourceMapper.insert(any(Datasource.class))).thenAnswer(inv -> {
            ((Datasource) inv.getArgument(0)).setId(DS_ID);
            return 1;
        });

        Long id = datasourceService.createDatasource(createDto("api", config), TENANT);

        assertThat(id).isEqualTo(DS_ID);
        ArgumentCaptor<Datasource> captor = ArgumentCaptor.forClass(Datasource.class);
        verify(datasourceMapper).insert(captor.capture());
        // config 是透传的不透明 JSON 字符串，原样存
        assertThat(captor.getValue().getConfig()).isEqualTo(config);
        verify(connectionFactory, never()).getTestConnection(anyString(), anyString());
        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("创建 - 未知类型码以 400 拒绝并列出内置支持的码（API-128：原先是冒到 catch-all 的 500）")
    void should_throwBizBadRequest_when_typeCodeUnknown() {
        assertThatThrownBy(() -> datasourceService.createDatasource(createDto("oracle9", jdbcConfig()), TENANT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知数据源类型: oracle9")
                .hasMessageContaining("mysql");

        verify(datasourceMapper, never()).insert(any(Datasource.class));
        verify(connectionFactory, never()).getTestConnection(anyString(), anyString());
    }

    @Test
    @DisplayName("更新 - 带未知类型码同样 400，且不落库")
    void should_throwBizBadRequest_when_updateCarriesUnknownType() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        DatasourceUpdateDTO dto = new DatasourceUpdateDTO();
        dto.setType("db2");

        assertThatThrownBy(() -> datasourceService.updateDatasource(DS_ID, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知数据源类型: db2");

        verify(datasourceMapper, never()).updateById(any(Datasource.class));
    }

    @Test
    @DisplayName("创建 - JDBC 连接无效时以业务异常拒绝，且在落库之前")
    void should_throwBizExceptionAndNotPersist_when_jdbcConnectionInvalid() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.isValid(5)).thenReturn(false);
        when(connectionFactory.getTestConnection(anyString(), anyString())).thenReturn(conn);

        assertThatThrownBy(() -> datasourceService.createDatasource(createDto("mysql", jdbcConfig()), TENANT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法建立有效连接");

        verify(datasourceMapper, never()).insert(any(Datasource.class));
    }

    @Test
    @DisplayName("创建 - 连接异常时透出上游原因而非口令，且不含密码值")
    void should_surfaceUpstreamReasonWithoutSecret_when_connectionThrows() {
        when(connectionFactory.getTestConnection(anyString(), anyString()))
                .thenThrow(new RuntimeException("Communications link failure on 10.0.0.9:3306"));

        assertThatThrownBy(() -> datasourceService.createDatasource(createDto("mysql", jdbcConfig()), TENANT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("连接失败: Communications link failure")
                .hasMessageNotContaining(SECRET);

        verify(datasourceMapper, never()).insert(any(Datasource.class));
    }

    // =======================================================================
    // updateDatasource — config 覆盖语义（API-106 数据丢失形态）
    // =======================================================================

    @Test
    @DisplayName("更新 - 带新 config 时按发送值覆盖存储并回收连接池")
    void should_overwriteConfigAndRemovePool_when_updateCarriesConfig() {
        Datasource existing = entity("mysql", jdbcConfig());
        when(datasourceMapper.selectById(DS_ID)).thenReturn(existing);
        String newConfig = "{\"host\":\"10.0.0.10\",\"port\":5432,\"database\":\"dw2\"}";
        DatasourceUpdateDTO dto = new DatasourceUpdateDTO();
        dto.setName("改名");
        dto.setType("postgresql");
        dto.setConfig(newConfig);
        dto.setStatus(0);
        dto.setDescription("d2");

        datasourceService.updateDatasource(DS_ID, dto);

        ArgumentCaptor<Datasource> captor = ArgumentCaptor.forClass(Datasource.class);
        verify(datasourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfig()).isEqualTo(newConfig);
        assertThat(captor.getValue().getName()).isEqualTo("改名");
        assertThat(captor.getValue().getType()).isEqualTo("postgresql");
        verify(connectionFactory).removePool(DS_ID);
    }

    @Test
    @DisplayName("更新 - dto 未带 config 时整个 DTO 被拷到实体、config 变 null 交给 updateById（未显式兜底）")
    void should_passNullConfigToUpdateById_when_dtoConfigMissing() {
        Datasource existing = entity("mysql", jdbcConfig());
        when(datasourceMapper.selectById(DS_ID)).thenReturn(existing);
        DatasourceUpdateDTO dto = new DatasourceUpdateDTO();
        dto.setName("只改名字");
        // 故意不设 config / type / status / description

        datasourceService.updateDatasource(DS_ID, dto);

        ArgumentCaptor<Datasource> captor = ArgumentCaptor.forClass(Datasource.class);
        verify(datasourceMapper).updateById(captor.capture());
        // 实际行为：整个 DTO（含 null）被覆盖到实体 —— 服务层没有区分"字段缺省"与"字段清空"，
        // 之所以库里旧凭据没被抹掉，纯属 MyBatis-Plus 默认 NOT_NULL 更新策略兜底，而非本服务主动防护
        assertThat(captor.getValue().getConfig()).isNull();
        assertThat(captor.getValue().getType()).isNull();
        assertThat(captor.getValue().getStatus()).isNull();
        assertThat(captor.getValue().getName()).isEqualTo("只改名字");
    }

    @Test
    @DisplayName("更新 - 提交空串 config 被拒绝，不再把已存凭据抹掉（API-126 已修）")
    void should_refuseUpdate_when_dtoConfigIsBlank() {
        Datasource existing = entity("mysql", jdbcConfig());
        when(datasourceMapper.selectById(DS_ID)).thenReturn(existing);
        DatasourceUpdateDTO dto = new DatasourceUpdateDTO();
        dto.setName("改名但没填配置");
        dto.setConfig("");   // 空串非 null，原先会越过 MP 的 NOT_NULL 策略被真正写库

        assertThatThrownBy(() -> datasourceService.updateDatasource(DS_ID, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("空的 config");

        // 一次写入都没发生：改名这种无害操作也不能以覆盖凭据为代价换成功
        verify(datasourceMapper, never()).updateById(any(Datasource.class));
        verify(connectionFactory, never()).removePool((Long) any());
    }

    @Test
    @DisplayName("更新 - 数据源不存在时按 DATASOURCE_NOT_FOUND 拒绝，且不写库不回收连接池")
    void should_throwNotFound_when_updateMissingRow() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(null);
        DatasourceUpdateDTO dto = new DatasourceUpdateDTO();
        dto.setName("x");

        assertThatThrownBy(() -> datasourceService.updateDatasource(DS_ID, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据源不存在");

        verify(datasourceMapper, never()).updateById(any(Datasource.class));
        verify(connectionFactory, never()).removePool(any(Long.class));
    }

    // =======================================================================
    // getDatasourceById — 详情带 config（列表不带）
    // =======================================================================

    @Test
    @DisplayName("详情 - 返回体携带 config 原文（含口令），字段逐一可见")
    void should_returnDetailWithConfig_when_exists() {
        String config = jdbcConfig();
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", config));

        DatasourceVO vo = datasourceService.getDatasourceById(DS_ID);

        assertThat(vo.getId()).isEqualTo(DS_ID);
        assertThat(vo.getName()).isEqualTo("生产库");
        assertThat(vo.getType()).isEqualTo("mysql");
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getTenantId()).isEqualTo(TENANT);
        // 详情刻意带 config（编辑表单需要回填），因此口令确实在这里返回给 datasource:read
        assertThat(vo.getConfig()).isEqualTo(config).contains(SECRET);
    }

    @Test
    @DisplayName("详情 - 数据源不存在时抛 DATASOURCE_NOT_FOUND")
    void should_throwNotFound_when_detailMissingRow() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(null);

        assertThatThrownBy(() -> datasourceService.getDatasourceById(DS_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据源不存在");
    }

    // =======================================================================
    // listDatasources — Wrapper SQL 片段 + 列表 VO 不得带 config
    // =======================================================================

    @Test
    @DisplayName("列表 - 带 name/type/status 过滤：Wrapper 拼出 tenant+like+eq+排序，VO 不含 config")
    void should_buildFullWrapperAndReturnConfigFreeVo_when_filtersProvided() {
        Datasource row = entity("mysql", jdbcConfig());
        Page<Datasource> dataPage = new Page<>(1, 10);
        dataPage.setRecords(Collections.singletonList(row));
        dataPage.setTotal(1L);
        when(datasourceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dataPage);

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<DatasourceListVO> result =
                datasourceService.listDatasources("订单", "mysql", 1, TENANT, query);

        ArgumentCaptor<LambdaQueryWrapper<Datasource>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(datasourceMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        LambdaQueryWrapper<Datasource> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment)
                .contains("tenant_id")
                .contains("name LIKE")
                .contains("type =")
                .contains("status =")
                .contains("ORDER BY create_time DESC");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(TENANT);
        assertThat(wrapper.getParamNameValuePairs()).containsValue("%订单%");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        DatasourceListVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(DS_ID);
        assertThat(vo.getName()).isEqualTo("生产库");
        assertThat(vo.getType()).isEqualTo("mysql");
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getDescription()).isEqualTo("desc");
        assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        // 列表 VO 根本没有 config 字段 —— 口令不会随 datasource:read 的列表端点外泄
        assertThat(fieldNames(DatasourceListVO.class)).doesNotContain("config");
    }

    @Test
    @DisplayName("列表 - name/type/status 全空时不拼多余过滤条件，仅剩 tenant 与排序")
    void should_omitOptionalFilters_when_blankCriteria() {
        Page<Datasource> dataPage = new Page<>(1, 10);
        dataPage.setRecords(Collections.<Datasource>emptyList());
        dataPage.setTotal(0L);
        when(datasourceMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dataPage);

        datasourceService.listDatasources(null, "", null, TENANT, new PageQuery());

        ArgumentCaptor<LambdaQueryWrapper<Datasource>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(datasourceMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        LambdaQueryWrapper<Datasource> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getSqlSegment()).doesNotContain("name LIKE").doesNotContain("type =")
                .doesNotContain("status =");
        // 只有 tenant 一个参数值
        assertThat(wrapper.getParamNameValuePairs()).hasSize(1).containsValue(TENANT);
    }

    // =======================================================================
    // testConnection(Long id) — guard :133，走连接池，不回传凭据
    // =======================================================================

    @Test
    @DisplayName("测连接(按 id) - 走已有连接池取连接并校验有效性，不额外发起一次性测试连接")
    void should_usePooledConnection_when_testByIdValid() throws Exception {
        String config = jdbcConfig();
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", config));
        Connection conn = mock(Connection.class);
        when(conn.isValid(5)).thenReturn(true);
        when(connectionFactory.getConnection(DS_ID, "mysql", config)).thenReturn(conn);

        boolean ok = datasourceService.testConnection(DS_ID);

        assertThat(ok).isTrue();
        verify(connectionFactory).getConnection(DS_ID, "mysql", config);
        // by-id 复用存储的连接池，不会把 config 再喂给一次性 getTestConnection，也不回传给调用方
        verify(connectionFactory, never()).getTestConnection(anyString(), anyString());
    }

    @Test
    @DisplayName("测连接(按 id) - 非 JDBC 类型拒绝并给出具体中文文案，且不建连（guard :133）")
    void should_throwNonJdbc_when_testByIdNonJdbc() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", jdbcConfig()));

        assertThatThrownBy(() -> datasourceService.testConnection(DS_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源类型不支持连接测试: mongodb")
                .hasMessageNotContaining("系统内部错误");

        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
        verify(connectionFactory, never()).getTestConnection(anyString(), anyString());
    }

    @Test
    @DisplayName("测连接(按 id) - 取连接失败时透出上游原因、不含口令、不是泛化的系统内部错误")
    void should_surfaceUpstreamReason_when_testByIdConnectionThrows() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("Access denied for user 'root'@'10.0.0.9'"));

        assertThatThrownBy(() -> datasourceService.testConnection(DS_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("连接测试失败: Access denied for user 'root'@'10.0.0.9'")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining("系统内部错误");
    }

    // =======================================================================
    // testConnection(TestConnectionDTO) — guard :149，ad-hoc 无需行存在
    // =======================================================================

    @Test
    @DisplayName("测连接(ad-hoc) - 不需要数据源行存在，直接用请求体的 type/config 建一次性测试连接")
    void should_notLoadRow_when_testByAdHocPayload() throws Exception {
        String config = jdbcConfig();
        Connection conn = mock(Connection.class);
        when(conn.isValid(5)).thenReturn(true);
        when(connectionFactory.getTestConnection("postgresql", config)).thenReturn(conn);
        TestConnectionDTO dto = new TestConnectionDTO();
        dto.setType("postgresql");
        dto.setConfig(config);

        boolean ok = datasourceService.testConnection(dto);

        assertThat(ok).isTrue();
        verify(datasourceMapper, never()).selectById(any(Long.class));
        verify(connectionFactory).getTestConnection("postgresql", config);
    }

    @Test
    @DisplayName("测连接(ad-hoc) - 非 JDBC 类型拒绝并给出具体中文文案，且完全不建连（guard :149）")
    void should_throwNonJdbc_when_testByAdHocNonJdbc() {
        TestConnectionDTO dto = new TestConnectionDTO();
        dto.setType("csv");
        dto.setConfig("{\"path\":\"/a.csv\"}");

        assertThatThrownBy(() -> datasourceService.testConnection(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源类型不支持连接测试: csv");

        verify(connectionFactory, never()).getTestConnection(anyString(), anyString());
    }

    // =======================================================================
    // getTables — guard :169 + PostgreSQL schema 分支 + 拒绝码不一致
    // =======================================================================

    @Test
    @DisplayName("取表 - PostgreSQL 走 schema=public 且 catalog 置空的分支")
    void should_usePublicSchemaBranch_when_getTablesPostgresql() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("postgresql", jdbcConfig()));
        Connection conn = mock(Connection.class);
        DatabaseMetaData dbMeta = mock(DatabaseMetaData.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.getMetaData()).thenReturn(dbMeta);
        when(conn.getCatalog()).thenReturn("dw");
        when(dbMeta.getTables(isNull(), eq("public"), eq("%"), any(String[].class))).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("TABLE_NAME")).thenReturn("orders");
        when(rs.getString("REMARKS")).thenReturn("订单表");
        when(rs.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);

        List<TableInfoVO> tables = datasourceService.getTables(DS_ID);

        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getTableName()).isEqualTo("orders");
        assertThat(tables.get(0).getTableComment()).isEqualTo("订单表");
        assertThat(tables.get(0).getTableType()).isEqualTo("TABLE");
        // POSTGRESQL 分支：catalog 传 null、schema 传 "public"
        verify(dbMeta).getTables(isNull(), eq("public"), eq("%"), any(String[].class));
    }

    @Test
    @DisplayName("取表 - 非 JDBC 拒绝文案具体，但 BizException 携带的是 500 码（缺陷锁现状，guard :169）")
    void should_throw500CodeWithSpecificMessage_when_getTablesNonJdbc() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("excel", jdbcConfig()));

        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> datasourceService.getTables(DS_ID));

        assertThat(ex.getMessage()).contains("非JDBC数据源不支持获取表列表");
        // 客户端错误却被标成 INTERNAL_SERVER_ERROR(500)：产线用的是 BizException(String) 单参构造
        assertThat(ex.getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    // =======================================================================
    // getTableColumns — guard :209 + 主键/可空映射
    // =======================================================================

    @Test
    @DisplayName("取列 - MySQL 元数据映射：主键列置 true、NULLABLE=1 映射为可空")
    void should_mapPrimaryKeyAndNullable_when_getColumnsMysql() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        Connection conn = mock(Connection.class);
        DatabaseMetaData dbMeta = mock(DatabaseMetaData.class);
        ResultSet pkRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);
        when(conn.getMetaData()).thenReturn(dbMeta);
        when(conn.getCatalog()).thenReturn("dw");
        when(dbMeta.getPrimaryKeys(eq("dw"), isNull(), eq("orders"))).thenReturn(pkRs);
        when(pkRs.next()).thenReturn(true, false);
        when(pkRs.getString("COLUMN_NAME")).thenReturn("id");
        when(dbMeta.getColumns(eq("dw"), isNull(), eq("orders"), eq("%"))).thenReturn(colRs);
        when(colRs.next()).thenReturn(true, true, false);
        when(colRs.getString("COLUMN_NAME")).thenReturn("id", "name");
        when(colRs.getString("TYPE_NAME")).thenReturn("INT", "VARCHAR");
        when(colRs.getInt("COLUMN_SIZE")).thenReturn(11, 64);
        when(colRs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNoNulls, DatabaseMetaData.columnNullable);
        when(colRs.getString("COLUMN_DEF")).thenReturn(null, null);
        when(colRs.getString("REMARKS")).thenReturn("主键", "名称");
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);

        List<ColumnInfoVO> columns = datasourceService.getTableColumns(DS_ID, "orders");

        assertThat(columns).hasSize(2);
        ColumnInfoVO id = columns.get(0);
        assertThat(id.getColumnName()).isEqualTo("id");
        assertThat(id.getDataType()).isEqualTo("INT");
        assertThat(id.getColumnSize()).isEqualTo(11);
        assertThat(id.getPrimaryKey()).isTrue();
        assertThat(id.getNullable()).isFalse();
        ColumnInfoVO name = columns.get(1);
        assertThat(name.getColumnName()).isEqualTo("name");
        assertThat(name.getPrimaryKey()).isFalse();
        assertThat(name.getNullable()).isTrue();
        assertThat(name.getComment()).isEqualTo("名称");
    }

    @Test
    @DisplayName("取列 - 非 JDBC 拒绝并给出具体中文文案，且不建连（guard :209）")
    void should_throwNonJdbc_when_getColumnsNonJdbc() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("api", jdbcConfig()));

        assertThatThrownBy(() -> datasourceService.getTableColumns(DS_ID, "orders"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源不支持获取列信息");

        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    // =======================================================================
    // executeQuery — guard :262 + 只读校验 + 截断 + 异常包装
    // =======================================================================

    @Test
    @DisplayName("执行查询 - 非只读 SQL 被拒且明确文案，且不建连")
    void should_rejectNonReadSql_when_executeUpdateStatement() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("DELETE FROM orders");

        assertThatThrownBy(() -> datasourceService.executeQuery(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只允许执行只读查询语句 (SELECT/SHOW/DESCRIBE)");

        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("执行查询 - 非 JDBC 类型拒绝并给出具体中文文案（guard :262）")
    void should_throwNonJdbc_when_executeQueryNonJdbc() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mongodb", jdbcConfig()));
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("SELECT 1");

        assertThatThrownBy(() -> datasourceService.executeQuery(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源不支持SQL查询");

        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("执行查询 - SELECT 成功返回列名/行/行数/未截断，并透传只读 SQL")
    void should_returnRows_when_selectSucceeds() throws Exception {
        givenQueryConnection("SELECT id, name FROM t", new String[]{"id", "name"},
                new Object[][]{{1, "a"}});
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("SELECT id, name FROM t");

        Map<String, Object> result = datasourceService.executeQuery(dto);

        assertThat(result.get("columns")).isEqualTo(Arrays.asList("id", "name"));
        assertThat(result.get("rowCount")).isEqualTo(1);
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat(result.get("sql")).isEqualTo("SELECT id, name FROM t");
        assertThat((List<?>) result.get("rows")).hasSize(1);
    }

    @Test
    @DisplayName("执行查询 - 达到 maxRows 上限时置 truncated=true 且只多取一行判定")
    void should_markTruncated_when_moreRowsThanMaxRows() throws Exception {
        // 3 行数据，但 maxRows=2 → 读满 2 行后又拿到第 3 行 → 判定被截断
        Connection conn = givenQueryConnection("SELECT id FROM t", new String[]{"id"},
                new Object[][]{{1}, {2}, {3}});
        Statement stmt = conn.createStatement();
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("SELECT id FROM t");
        dto.setMaxRows(2);
        dto.setTimeout(7);

        Map<String, Object> result = datasourceService.executeQuery(dto);

        assertThat(result.get("rowCount")).isEqualTo(2);
        assertThat(result.get("truncated")).isEqualTo(true);
        verify(stmt).setMaxRows(3);
        verify(stmt).setQueryTimeout(7);
    }

    @Test
    @DisplayName("执行查询 - 执行报 SQL 错时包装上游原因，不是泛化系统内部错误")
    void should_wrapSqlReason_when_executeQueryThrows() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenThrow(new SQLException("Unknown column 'x'"));
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("SELECT x FROM t");

        assertThatThrownBy(() -> datasourceService.executeQuery(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SQL执行失败: Unknown column 'x'")
                .hasMessageNotContaining("系统内部错误");
    }

    @Test
    @DisplayName("执行查询 - 查询超时时以「查询超时」文案拒绝")
    void should_throwTimeout_when_executeQueryTimesOut() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenThrow(new SQLTimeoutException("timeout exceeded"));
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);
        QueryExecuteDTO dto = new QueryExecuteDTO();
        dto.setDatasourceId(DS_ID);
        dto.setSql("SELECT sleep(9999)");

        assertThatThrownBy(() -> datasourceService.executeQuery(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("查询超时");
    }

    // =======================================================================
    // deleteDatasource — 无存在性/引用校验（锁现状）
    // =======================================================================

    @Test
    @DisplayName("删除 - 先回收连接池再按 id 调 mapper 删除")
    void should_removePoolThenDeleteById_when_delete() {
        datasourceService.deleteDatasource(DS_ID);

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(connectionFactory).removePool(idCaptor.capture());
        verify(datasourceMapper).deleteById(DS_ID);
        assertThat(idCaptor.getValue()).isEqualTo(DS_ID);
    }

    @Test
    @DisplayName("删除 - 既不做存在性校验也不做引用校验：任何 id 都会直接 deleteById（缺陷锁现状）")
    void should_deleteUnconditionally_when_noExistenceOrReferenceGuard() {
        datasourceService.deleteDatasource(999L);

        // 删除路径压根没查过库，也没被任何 dataset/ETL 引用守卫拦下
        verify(datasourceMapper, never()).selectById(any(Long.class));
        verify(datasourceMapper).deleteById(999L);
    }

    // ---- shared query-connection scaffolding -------------------------------

    /**
     * 搭一个 SELECT 场景的连接：列名取 {@code labels}，逐行数据取 {@code data}，
     * {@code rs.next()} 按 data 行数返回 TRUE…FALSE；{@code getObject(col)} 按行返回对应值。
     * 返回挂在 Statement/ResultSet 上的 Connection，供调用方进一步 verify。
     */
    private Connection givenQueryConnection(String sql, String[] labels, Object[][] data) throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(entity("mysql", jdbcConfig()));
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(sql)).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        int columnCount = labels.length;
        when(meta.getColumnCount()).thenReturn(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            when(meta.getColumnLabel(i)).thenReturn(labels[i - 1]);
        }
        // next(): 前 data.length 次 TRUE，最后一次 FALSE（行数恰好等于 data 长度）
        boolean[] nextSeq = new boolean[data.length + 1];
        for (int r = 0; r < data.length; r++) {
            nextSeq[r] = true;
        }
        stubNext(rs, nextSeq);
        for (int col = 1; col <= columnCount; col++) {
            Object[] perRow = new Object[data.length];
            for (int r = 0; r < data.length; r++) {
                perRow[r] = data[r][col - 1];
            }
            stubObject(rs, col, perRow);
        }
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);
        return conn;
    }

    private static void stubNext(ResultSet rs, boolean[] seq) throws SQLException {
        org.mockito.stubbing.OngoingStubbing<Boolean> stub = when(rs.next());
        for (boolean value : seq) {
            stub = stub.thenReturn(value);
        }
    }

    private static void stubObject(ResultSet rs, int col, Object[] perRow) throws SQLException {
        org.mockito.stubbing.OngoingStubbing<Object> stub = when(rs.getObject(col));
        for (Object value : perRow) {
            stub = stub.thenReturn(value);
        }
    }

    private static List<String> fieldNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            names.add(f.getName());
        }
        return names;
    }
}
