package com.dataviz.datasource.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.datasource.dto.WriteBatchDTO;
import com.dataviz.datasource.entity.Datasource;
import com.dataviz.datasource.factory.DataSourceConnectionFactory;
import com.dataviz.datasource.mapper.DatasourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结构化批量写入（ETL 目标端落库的真实通道）。
 * <p>
 * 测的是"这条通道有多宽"：拼出什么 SQL、什么输入会被拒掉、失败时事务怎么处理。
 * 这些判据一旦回退，症状是不可回滚的数据被写坏，而不是接口报错。
 */
@ExtendWith(MockitoExtension.class)
class DatasourceServiceImplWriteBatchTest {

    @Mock
    private DatasourceMapper datasourceMapper;

    @Mock
    private DataSourceConnectionFactory connectionFactory;

    @InjectMocks
    private DatasourceServiceImpl datasourceService;

    private static final long DS_ID = 7L;

    private Datasource mysqlDatasource() {
        Datasource datasource = new Datasource();
        datasource.setId(DS_ID);
        datasource.setType("mysql");
        datasource.setConfig("{\"host\":\"127.0.0.1\",\"port\":3306,\"database\":\"db\"}");
        return datasource;
    }

    private WriteBatchDTO dto(String table, List<String> columns, List<List<Object>> rows) {
        WriteBatchDTO body = new WriteBatchDTO();
        body.setDatasourceId(DS_ID);
        body.setTable(table);
        body.setColumns(columns);
        body.setRows(rows);
        return body;
    }

    private List<List<Object>> twoRows() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.<Object>asList(1, "paid"));
        rows.add(Arrays.<Object>asList(2, "refund"));
        return rows;
    }

    private Connection givenConnection(int... batchCounts) throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.getAutoCommit()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        // 校验类用例会在校验处就退出，拿不到 executeBatch —— 不放开严格 stub 检查的话，
        // 这些用例是"断言没跑、先被 UnnecessaryStubbing 判失败"
        lenient().when(ps.executeBatch()).thenReturn(batchCounts);
        when(connectionFactory.getConnection(any(Long.class), any(String.class), any(String.class)))
                .thenReturn(conn);
        when(datasourceMapper.selectById(DS_ID)).thenReturn(mysqlDatasource());
        return conn;
    }

    @Test
    @DisplayName("批量写入 - 数据源不存在时拒绝，不碰连接")
    void should_throwBizException_when_datasourceNotFound() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(null);

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), twoRows())))
                .isInstanceOf(BizException.class);

        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("批量写入 - 非 JDBC 数据源直接拒掉")
    void should_throwBizException_when_datasourceIsNotJdbc() {
        Datasource api = mysqlDatasource();
        api.setType("api");
        when(datasourceMapper.selectById(DS_ID)).thenReturn(api);

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), twoRows())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非JDBC数据源不支持批量写入");
    }

    @Test
    @DisplayName("批量写入 - 表名不合法时报出原名")
    void should_throwBizException_when_tableNameIllegal() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(mysqlDatasource());

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders; DROP TABLE x", Arrays.asList("id"), twoRows())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标表名不合法");
    }

    @Test
    @DisplayName("批量写入 - 重复列名在入库前报出列名，而不是等 MySQL 报错")
    void should_throwBizException_when_columnsDuplicated() {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(mysqlDatasource());

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "ID"), twoRows())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("写入列重复: ID");
    }

    @Test
    @DisplayName("批量写入 - 空行集返回 0 且不建连接：上游筛光不是失败")
    void should_returnZero_when_rowsEmpty() throws Exception {
        when(datasourceMapper.selectById(DS_ID)).thenReturn(mysqlDatasource());

        int written = datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), Collections.<List<Object>>emptyList()));

        assertThat(written).isZero();
        verify(connectionFactory, never()).getConnection(any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("批量写入 - 拼出参数化 INSERT，整批提交并返回真实写入行数")
    void should_commitAndReturnCounts_when_batchSucceeds() throws Exception {
        Connection conn = givenConnection(1, 1);

        int written = datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), twoRows()));

        assertThat(written).isEqualTo(2);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        assertThat(sql.getValue()).isEqualTo("INSERT INTO orders (id, status) VALUES (?, ?)");
        verify(conn).setAutoCommit(false);
        verify(conn).commit();
        verify(conn).close();
    }

    @Test
    @DisplayName("批量写入 - 驱动只回 SUCCESS_NO_INFO 时按每行 1 计数，统计不会恒为 0")
    void should_countOnePerStatement_when_driverOmitsCounts() throws Exception {
        givenConnection(Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO);

        int written = datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), twoRows()));

        assertThat(written).isEqualTo(2);
    }

    @Test
    @DisplayName("批量写入 - 某行取值个数与列数不符时回滚且不提交")
    void should_rollback_when_rowArityMismatch() throws Exception {
        Connection conn = givenConnection();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.<Object>asList(1));

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), rows)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("第 1 行的取值个数与写入列不一致（需要 2 个）");

        verify(conn).rollback();
        verify(conn, never()).commit();
    }

    @Test
    @DisplayName("批量写入 - 非标量取值被拒：嵌套对象写进去就是一列脏文本")
    void should_throwBizException_when_valueIsNotScalar() throws Exception {
        Connection conn = givenConnection();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.<Object>asList(nested, "paid"));

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), rows)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是标量值");

        verify(conn).rollback();
    }

    @Test
    @DisplayName("批量写入 - executeBatch 抛 SQLException 时包成业务异常并回滚")
    void should_rollbackAndWrap_when_executeBatchFails() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.getAutoCommit()).thenReturn(true);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeBatch()).thenThrow(new java.sql.SQLException("Data too long for column 'status'"));
        when(connectionFactory.getConnection(any(Long.class), anyString(), anyString())).thenReturn(conn);
        when(datasourceMapper.selectById(DS_ID)).thenReturn(mysqlDatasource());

        assertThatThrownBy(() -> datasourceService.writeBatch(
                dto("orders", Arrays.asList("id", "status"), twoRows())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("批量写入失败");

        verify(conn).rollback();
        verify(conn, never()).commit();
    }

    @Test
    @DisplayName("批量写入 - 逐列绑定，值按 columns 顺序落到对应占位符")
    void should_bindValuesInColumnOrder_when_writing() throws Exception {
        Connection conn = givenConnection(1, 1);
        PreparedStatement ps = conn.prepareStatement("any");

        datasourceService.writeBatch(dto("orders", Arrays.asList("id", "status"), twoRows()));

        verify(ps, times(4)).setObject(anyInt(), any());
        verify(ps, times(2)).addBatch();
    }
}
