package com.dataviz.ai.schema;

import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * schema 读取只允许"降级"，不允许"打断"：取不到表结构时要带原因回来，让 NL2SQL 把置信度压低并写进 llmNote，
 * 而不是抛异常把整个接口 500。这里同时钉住发给数据源的确实是 information_schema 那条只读查询。
 */
@ExtendWith(MockitoExtension.class)
class DatasourceSchemaReaderTest {

    @Mock
    private DatasourceQueryClient queryClient;

    @Test
    @DisplayName("没给数据源 id：直接判定无表结构，不发查询")
    void should_returnNoteWithoutQuery_whenDatasourceIdMissing() {
        DatasourceSchemaReader reader = new DatasourceSchemaReader(queryClient);

        DatasourceSchemaReader.SchemaResult result = reader.read(null);

        assertFalse(result.isPresent());
        assertEquals(0, result.getColumnCount());
        assertTrue(result.getNote().contains("未指定数据源"));
        verify(queryClient, never()).query(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("空结果集要说清是哪个数据源没读到，而不是留一句空白")
    void should_returnNote_whenQueryReturnsNoRows() {
        DatasourceSchemaReader reader = new DatasourceSchemaReader(queryClient);
        when(queryClient.query(eq(7L), any(), anyInt(), anyInt())).thenReturn(new DatasourceQueryResult());

        DatasourceSchemaReader.SchemaResult result = reader.read(7L);

        assertFalse(result.isPresent());
        assertTrue(result.getNote().contains("7"));
    }

    @Test
    @DisplayName("取数失败当降级处理：异常不外抛，原因写进 note")
    void should_degradeNotThrow_whenQueryFails() {
        DatasourceSchemaReader reader = new DatasourceSchemaReader(queryClient);
        when(queryClient.query(eq(7L), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("datasource-service 503"));

        DatasourceSchemaReader.SchemaResult result = reader.read(7L);

        assertFalse(result.isPresent());
        assertTrue(result.getNote().contains("503"));
    }

    @Test
    @DisplayName("渲染按表分组，列带类型与注释；列名大小写不一致也要认")
    void should_renderGroupedByTable_ignoringColumnNameCase() {
        DatasourceSchemaReader reader = new DatasourceSchemaReader(queryClient);
        List<Map<String, Object>> rows = Arrays.asList(
                row("TABLE_NAME", "t_order", "COLUMN_NAME", "id", "DATA_TYPE", "bigint", "COLUMN_COMMENT", "订单号"),
                row("table_name", "t_order", "column_name", "create_time", "data_type", "datetime", "column_comment", ""),
                row("table_name", "t_user", "column_name", "name", "data_type", "varchar", "column_comment", "昵称"));
        when(queryClient.query(eq(7L), any(), anyInt(), anyInt())).thenReturn(result(rows));

        DatasourceSchemaReader.SchemaResult read = reader.read(7L);

        assertTrue(read.isPresent());
        assertEquals(3, read.getColumnCount());
        String schema = read.getSchema();
        assertTrue(schema.contains("表名: t_order"), schema);
        assertTrue(schema.contains("  - id bigint -- 订单号"), schema);
        assertTrue(schema.contains("  - create_time datetime"), schema);
        assertFalse(schema.contains("create_time datetime --"), "无注释的列不该拖一个空注释分隔符");
        assertTrue(schema.indexOf("t_user") > schema.indexOf("t_order"), "保持返回顺序，别把表名打乱");
    }

    @Test
    @DisplayName("表名为空的行跳过：这类行渲染出来只会是 {@code 表名: } 空壳，对模型是噪声")
    void should_skipRowsWithoutTableName() {
        DatasourceSchemaReader reader = new DatasourceSchemaReader(queryClient);
        when(queryClient.query(eq(7L), any(), anyInt(), anyInt()))
                .thenReturn(result(Collections.singletonList(row("table_name", "", "column_name", "x"))));

        DatasourceSchemaReader.SchemaResult read = reader.read(7L);

        assertFalse(read.isPresent());
        assertEquals(1, read.getColumnCount());
    }

    private static DatasourceQueryResult result(List<Map<String, Object>> rows) {
        DatasourceQueryResult result = new DatasourceQueryResult();
        result.setColumns(new ArrayList<>(rows.get(0).keySet()));
        result.setRows(rows);
        result.setRowCount(rows.size());
        return result;
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
