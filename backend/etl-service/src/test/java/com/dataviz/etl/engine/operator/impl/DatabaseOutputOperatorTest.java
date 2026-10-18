package com.dataviz.etl.engine.operator.impl;

import com.dataviz.common.core.client.DatasourceWriteClient;
import com.dataviz.common.core.exception.BizException;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 目标端节点：把上游行落到库里。
 * <p>
 * 这里最要紧的是"不悄悄降级"：设计器选了 overwrite/upsert 时不能顺手当成 append 执行。
 */
@ExtendWith(MockitoExtension.class)
class DatabaseOutputOperatorTest {

    @Mock
    private DatasourceWriteClient writeClient;

    @InjectMocks
    private DatabaseOutputOperator operator;

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private NodeContext context(Map<String, Object> config, List<Map<String, Object>> input) {
        return NodeContext.builder()
                .nodeId("sink-1")
                .operatorType("output")
                .config(config)
                .inputData(input)
                .build();
    }

    @Test
    @DisplayName("目标端 - append 模式按上游首行的键序写入，rowCount 就是实际写入行数")
    void should_writeRowsInColumnOrder_when_appendMode() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");
        List<Map<String, Object>> input = Arrays.asList(
                row("region", "华东", "amount", 12.5),
                row("region", "华南", "amount", 7.0));
        when(writeClient.write(any(Long.class), anyString(), anyList(), anyList())).thenReturn(2);

        NodeResult result = operator.execute(context(config, input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getMetadata()).containsEntry("targetTable", "orders_summary")
                .containsEntry("rowsWritten", 2);

        ArgumentCaptor<List<String>> columns = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<List<Object>>> rowsArg = ArgumentCaptor.forClass(List.class);
        verify(writeClient).write(any(Long.class), anyString(), columns.capture(), rowsArg.capture());
        assertThat(columns.getValue()).containsExactly("region", "amount");
        assertThat(rowsArg.getValue()).containsExactly(
                Arrays.<Object>asList("华东", 12.5), Arrays.<Object>asList("华南", 7.0));
    }

    @Test
    @DisplayName("目标端 - 演示数据用 mode 键而不是 writeMode：两种都接")
    void should_acceptModeKey_when_writeModeAbsent() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");
        config.put("mode", "APPEND");
        when(writeClient.write(anyLong(), anyString(), anyList(), anyList())).thenReturn(1);

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("id", 1))));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("目标端 - overwrite 明确失败，而不是被当成 append 悄悄执行")
    void should_failLoudly_when_writeModeIsOverwrite() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");
        config.put("writeMode", "overwrite");

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("id", 1))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("暂不支持: overwrite");
        verify(writeClient, never()).write(anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("目标端 - 缺少 datasourceId 时不知道写进哪个库，直接失败")
    void should_fail_when_datasourceIdMissing() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("tableName", "orders_summary");

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("id", 1))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("缺少 datasourceId");
    }

    @Test
    @DisplayName("目标端 - 表名不合法时不发起写入")
    void should_fail_when_tableNameIllegal() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders; DROP TABLE x");

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("id", 1))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("目标表名不合法");
        verify(writeClient, never()).write(anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("目标端 - 上游筛光时记 0 行成功，而不是把任务标红")
    void should_returnZeroRowsSuccessfully_when_inputEmpty() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");

        NodeResult result = operator.execute(context(config, new ArrayList<Map<String, Object>>()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isZero();
        verify(writeClient, never()).write(anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    @DisplayName("目标端 - 写入失败被转成节点失败，原话保留在错误信息里")
    void should_wrapClientFailure_when_writeThrows() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");
        when(writeClient.write(anyLong(), anyString(), anyList(), anyList()))
                .thenThrow(new BizException(com.dataviz.common.core.result.ErrorCode.SQL_EXECUTION_ERROR,
                        "批量写入失败: Field 'id' doesn't have a default value"));

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("region", "华东"))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("目标端写入失败").contains("doesn't have a default value");
    }

    @Test
    @DisplayName("目标端 - 显式 columns 决定写入列，上游多余列不写进去")
    void should_useConfiguredColumns_when_columnsGiven() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 5);
        config.put("tableName", "orders_summary");
        config.put("columns", "region");
        when(writeClient.write(anyLong(), anyString(), anyList(), anyList())).thenReturn(1);

        NodeResult result = operator.execute(context(config,
                Collections.singletonList(row("region", "华东", "amount", 12.5))));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<List<String>> columns = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<List<Object>>> rowsArg = ArgumentCaptor.forClass(List.class);
        verify(writeClient).write(anyLong(), anyString(), columns.capture(), rowsArg.capture());
        assertThat(columns.getValue()).containsExactly("region");
        assertThat(rowsArg.getValue()).containsExactly(Collections.<Object>singletonList("华东"));
    }
}
