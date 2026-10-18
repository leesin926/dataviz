package com.dataviz.etl.engine;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DAG 执行的记账口径：读数是源端读到的，写数是目标端写进去的，中间节点两边都不进。
 * <p>
 * 这里也管"失败/停止时账不能抹平"：历史列表里的读数要能看出这一批跑到过哪一步。
 */
@ExtendWith(MockitoExtension.class)
class DagExecutorAccountingTest {

    private static final String SOURCE_DAG = "{\"nodes\":["
            + "{\"id\":\"n1\",\"type\":\"input\",\"config\":{\"datasourceId\":1}},"
            + "{\"id\":\"n2\",\"type\":\"output\",\"config\":{\"datasourceId\":1}}],"
            + "\"edges\":[{\"source\":\"n1\",\"target\":\"n2\"}]}";

    @Mock
    private OperatorFactory operatorFactory;

    private DagExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DagExecutor(operatorFactory, new ObjectMapper());
    }

    private NodeResult rows(int count, boolean truncated) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i);
            data.add(row);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("truncated", truncated);
        return NodeResult.builder()
                .success(true)
                .outputData(data)
                .rowCount(count)
                .durationMs(1)
                .metadata(metadata)
                .build();
    }

    /** 记账按算子注册名判断，DAG 里写的是节点别名，所以桩要给出映射后的名字 */
    private static String canonicalOf(String nodeType) {
        if ("input".equals(nodeType) || "source".equals(nodeType)) {
            return "database_input";
        }
        if ("output".equals(nodeType) || "sink".equals(nodeType)) {
            return "database_output";
        }
        return nodeType;
    }

    private void stubOperator(String nodeType, NodeResult result) {
        Operator operator = new Operator() {
            @Override
            public NodeResult execute(NodeContext context) {
                return result;
            }

            @Override
            public String getType() {
                return nodeType;
            }
        };
        lenient().when(operatorFactory.canonicalType(nodeType)).thenReturn(canonicalOf(nodeType));
        lenient().when(operatorFactory.getOperator(nodeType)).thenReturn(operator);
    }

    /** 让某个节点在 execute 里做额外动作（比如模拟"跑一半被停止"） */
    private interface NodeBehaviour {
        NodeResult apply(NodeContext context);
    }

    private void stubOperatorAnswer(String nodeType, NodeBehaviour behaviour) {
        Operator operator = new Operator() {
            @Override
            public NodeResult execute(NodeContext context) {
                return behaviour.apply(context);
            }

            @Override
            public String getType() {
                return nodeType;
            }
        };
        lenient().when(operatorFactory.canonicalType(nodeType)).thenReturn(canonicalOf(nodeType));
        lenient().when(operatorFactory.getOperator(nodeType)).thenReturn(operator);
    }

    @Test
    @DisplayName("记账 - 源端读 10 行、目标端写 8 行时两个数分别入账")
    void should_separateReadAndWrite_when_sourceAndSinkBothRun() {
        stubOperator("input", rows(10, false));
        stubOperator("output", rows(8, false));

        DagRunSummary summary = executor.execute(SOURCE_DAG, 1L);

        assertThat(summary.getRowsRead()).isEqualTo(10);
        assertThat(summary.getRowsWritten()).isEqualTo(8);
        assertThat(summary.getExecutedNodes()).isEqualTo(2);
        assertThat(summary.getPlannedNodes()).isEqualTo(2);
        assertThat(summary.isCancelled()).isFalse();
        assertThat(summary.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("记账 - 中间节点的行数不重复计入读数")
    void should_countSourceOnly_when_intermediateNodeInChain() {
        String dag = "{\"nodes\":["
                + "{\"id\":\"n1\",\"type\":\"input\"},"
                + "{\"id\":\"n2\",\"type\":\"filter\"},"
                + "{\"id\":\"n3\",\"type\":\"output\"}],"
                + "\"edges\":[{\"source\":\"n1\",\"target\":\"n2\"},{\"source\":\"n2\",\"target\":\"n3\"}]}";
        stubOperator("input", rows(10, false));
        stubOperator("filter", rows(4, false));
        stubOperator("output", rows(4, false));

        DagRunSummary summary = executor.execute(dag, 2L);

        assertThat(summary.getRowsRead()).isEqualTo(10);
        assertThat(summary.getRowsWritten()).isEqualTo(4);
        assertThat(summary.getExecutedNodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("截断 - 源端报 truncated 时给出提示，不能只写进服务日志")
    void should_warn_when_sourceTruncated() {
        stubOperator("input", rows(10, true));
        stubOperator("output", rows(10, false));

        DagRunSummary summary = executor.execute(SOURCE_DAG, 3L);

        assertThat(summary.getWarnings()).hasSize(1);
        assertThat(summary.getWarnings().get(0))
                .contains("源端节点 n1")
                .contains("上限 " + DagRunSummary.READ_ROW_CEILING);
        assertThat(summary.incomplete()).isTrue();
    }

    @Test
    @DisplayName("失败 - 目标端失败时把源端已读到的行数带出来，不抹成 0")
    void should_carryPartialCounts_when_sinkFails() {
        stubOperator("input", rows(10, false));
        Operator failing = new Operator() {
            @Override
            public NodeResult execute(NodeContext context) {
                return NodeResult.failure("目标端写入失败: Field 'id' doesn't have a default value");
            }

            @Override
            public String getType() {
                return "output";
            }
        };
        when(operatorFactory.canonicalType("output")).thenReturn("database_output");
        when(operatorFactory.getOperator("output")).thenReturn(failing);

        assertThatThrownBy(() -> executor.execute(SOURCE_DAG, 4L))
                .isInstanceOf(DagExecutionException.class)
                .hasMessageContaining("doesn't have a default value")
                .satisfies(thrown -> {
                    DagRunSummary partial = ((DagExecutionException) thrown).getPartial();
                    assertThat(partial).isNotNull();
                    assertThat(partial.getRowsRead()).isEqualTo(10);
                    assertThat(partial.getRowsWritten()).isZero();
                });
    }

    @Test
    @DisplayName("失败 - DAG 还没跑出任何节点结果时不编造部分账")
    void should_haveNoPartial_when_dagNeverRuns() {
        lenient().when(operatorFactory.canonicalType(anyString())).thenReturn("input");
        when(operatorFactory.getOperator("input")).thenThrow(new IllegalArgumentException("Unknown operator type: input"));

        assertThatThrownBy(() -> executor.execute(SOURCE_DAG, 5L))
                .isInstanceOf(DagExecutionException.class)
                .satisfies(thrown -> assertThat(((DagExecutionException) thrown).getPartial()).isNull());
    }

    @Test
    @DisplayName("停止 - 跑到一半按停止：记为已取消，已完成节点的读数保留")
    void should_reportCancelled_when_stoppedMidRun() {
        stubOperatorAnswer("input", new NodeBehaviour() {
            @Override
            public NodeResult apply(NodeContext context) {
                executor.stop(context.getInstanceId());
                return rows(10, false);
            }
        });
        stubOperator("output", rows(8, false));

        DagRunSummary summary = executor.execute(SOURCE_DAG, 6L);

        assertThat(summary.isCancelled()).isTrue();
        assertThat(summary.getRowsRead()).isEqualTo(10);
        assertThat(summary.getRowsWritten()).isZero();
        assertThat(summary.getExecutedNodes()).isEqualTo(1);
        assertThat(summary.incomplete()).isTrue();
    }

    @Test
    @DisplayName("校验 - 空节点清单明确报错，不返回一个「成功」的空账")
    void should_fail_when_dagHasNoNodes() {
        assertThatThrownBy(() -> executor.execute("{\"nodes\":[]}", 7L))
                .isInstanceOf(DagExecutionException.class)
                .hasMessageContaining("DAG has no nodes");
    }

    @Test
    @DisplayName("校验 - 成环的 DAG 报错，不会静默只跑一部分")
    void should_fail_when_dagHasCycle() {
        String cyclic = "{\"nodes\":[{\"id\":\"a\",\"type\":\"input\"},{\"id\":\"b\",\"type\":\"output\"}],"
                + "\"edges\":[{\"source\":\"a\",\"target\":\"b\"},{\"source\":\"b\",\"target\":\"a\"}]}";

        assertThatThrownBy(() -> executor.execute(cyclic, 8L))
                .isInstanceOf(DagExecutionException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("计划 - plannedNodes 是清单里的节点总数，供「执行被停止：计划 N 完成 M」这句话用")
    void should_countPlannedNodes_when_singleNodeDag() {
        stubOperator("input", rows(1, false));

        DagRunSummary summary = executor.execute(
                "{\"nodes\":[{\"id\":\"only\",\"type\":\"input\"}]}", 9L);

        assertThat(summary.getPlannedNodes()).isEqualTo(1);
        assertThat(summary.getRowsRead()).isEqualTo(1);
    }
}
