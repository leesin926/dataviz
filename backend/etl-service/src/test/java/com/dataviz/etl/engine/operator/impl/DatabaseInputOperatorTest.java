package com.dataviz.etl.engine.operator.impl;

import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 源端节点：真正从库里把行读出来。
 * <p>
 * 之前的版本不连库、直接把计划行数当成读取数，任务历史因此全是"读了 N 行"的成功假象。
 * 这里守住两件事：SQL 是按配置拼出来的，读数是查询返回的。
 */
@ExtendWith(MockitoExtension.class)
class DatabaseInputOperatorTest {

    @Mock
    private DatasourceQueryClient queryClient;

    @InjectMocks
    private DatabaseInputOperator operator;

    private NodeContext context(Map<String, Object> config) {
        return NodeContext.builder()
                .nodeId("src-1")
                .operatorType("input")
                .config(config)
                .inputData(new ArrayList<Map<String, Object>>())
                .build();
    }

    private DatasourceQueryResult result(int rows, boolean truncated) {
        DatasourceQueryResult queryResult = new DatasourceQueryResult();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            list.add(row);
        }
        queryResult.setRows(list);
        queryResult.setRowCount(rows);
        queryResult.setTruncated(truncated);
        return queryResult;
    }

    @Test
    @DisplayName("源端 - 表名模式拼出 SELECT 列/WHERE/LIMIT，读数取查询返回的行数")
    void should_buildSelectAndCountRealRows_when_tableMode() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        config.put("columns", "id, pay_amount");
        config.put("whereClause", "pay_amount > 100");
        config.put("limit", 500);
        when(queryClient.query(anyLong(), anyString(), any(), isNull()))
                .thenReturn(result(2, false));

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.isSuccess()).isTrue();
        assertThat(nodeResult.getRowCount()).isEqualTo(2);
        assertThat(nodeResult.getOutputData()).hasSize(2);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(queryClient).query(eq(3L), sql.capture(), eq(5000), isNull());
        assertThat(sql.getValue()).isEqualTo(
                "SELECT id, pay_amount FROM orders WHERE pay_amount > 100 LIMIT 500");
    }

    @Test
    @DisplayName("源端 - 未配列时投影为 *，且不拼 LIMIT")
    void should_selectAll_when_columnsAndLimitAbsent() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        when(queryClient.query(anyLong(), anyString(), any(), isNull())).thenReturn(result(0, false));

        operator.execute(context(config));

        verify(queryClient).query(eq(3L), eq("SELECT * FROM orders"), eq(5000), isNull());
    }

    @Test
    @DisplayName("源端 - 手写 SQL 优先于表名模式，原样交给只读通道")
    void should_preferRawSql_when_sqlConfigured() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "ignored_table");
        config.put("sql", "  SELECT 1 AS ok  ");
        when(queryClient.query(anyLong(), anyString(), any(), isNull())).thenReturn(result(1, false));

        operator.execute(context(config));

        verify(queryClient).query(eq(3L), eq("SELECT 1 AS ok"), eq(5000), isNull());
    }

    @Test
    @DisplayName("源端 - limit 超过天花板时被夹到天花板，不向服务端提越界请求")
    void should_clampLimit_when_limitExceedsCeiling() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        config.put("limit", 999999);
        when(queryClient.query(anyLong(), anyString(), any(), isNull())).thenReturn(result(0, false));

        operator.execute(context(config));

        verify(queryClient).query(eq(3L), eq("SELECT * FROM orders LIMIT 5000"), eq(5000), isNull());
    }

    @Test
    @DisplayName("源端 - 读满上限时把 truncated 带进元数据，供上层报「结果可能不完整」")
    void should_exposeTruncatedFlag_when_resultTruncated() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        when(queryClient.query(anyLong(), anyString(), any(), isNull()))
                .thenReturn(result(5000, true));

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.getMetadata()).containsEntry("truncated", true);
        assertThat(nodeResult.getRowCount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("源端 - 缺少 datasourceId 时不发起查询")
    void should_failWithoutQuery_when_datasourceIdMissing() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tableName", "orders");

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.isSuccess()).isFalse();
        assertThat(nodeResult.getErrorMessage()).contains("缺少 datasourceId");
        verify(queryClient, never()).query(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("源端 - 表名不合法时明确失败，不去拼一条形状怪异的 SQL")
    void should_fail_when_tableNameIllegal() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders`");

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.isSuccess()).isFalse();
        assertThat(nodeResult.getErrorMessage()).contains("源表名不合法");
    }

    @Test
    @DisplayName("源端 - 查询失败转成节点失败，服务端原话保留")
    void should_wrapQueryFailure_when_clientThrows() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        when(queryClient.query(anyLong(), anyString(), any(), isNull()))
                .thenThrow(new IllegalStateException("Table 'db.orders' doesn't exist"));

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.isSuccess()).isFalse();
        assertThat(nodeResult.getErrorMessage()).contains("源端读取失败").contains("doesn't exist");
    }

    @Test
    @DisplayName("源端 - 服务返回 null 行集时按 0 行处理，不抛空指针")
    void should_treatNullRowsAsEmpty_when_serviceReturnsNullRows() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 3);
        config.put("tableName", "orders");
        DatasourceQueryResult empty = new DatasourceQueryResult();
        empty.setRows(null);
        when(queryClient.query(anyLong(), anyString(), any(), isNull())).thenReturn(empty);

        NodeResult nodeResult = operator.execute(context(config));

        assertThat(nodeResult.isSuccess()).isTrue();
        assertThat(nodeResult.getOutputData()).isEmpty();
    }

    @Test
    @DisplayName("源端 - 算子注册类型是 database_input，节点别名映射由工厂负责")
    void should_reportCanonicalType_when_asked() {
        assertThat(operator.getType()).isEqualTo("database_input");
    }
}
