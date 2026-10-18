package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合节点的配置形状与取值判据。
 * <p>
 * 两种汇总写法都真实存在：设计器写 aggregations 数组，演示数据写 {"SUM(pay_amount)":"amount"} 简写。
 * 认不了必须报错 —— 猜成整组计数的话，症状是汇总值差几个数量级却一路成功写进目标表。
 */
class AggregateOperatorTest {

    private final AggregateOperator operator = new AggregateOperator();

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private NodeContext context(Map<String, Object> config, List<Map<String, Object>> input) {
        return NodeContext.builder()
                .nodeId("agg-1")
                .operatorType("aggregate")
                .config(config)
                .inputData(input)
                .build();
    }

    private List<Map<String, Object>> orders() {
        return Arrays.asList(
                row("region", "华东", "pay_amount", 100.0),
                row("region", "华东", "pay_amount", 50.0),
                row("region", "华南", "pay_amount", 30.0));
    }

    private Map<String, Object> aggregation(String field, String function, String alias) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("function", function);
        map.put("alias", alias);
        return map;
    }

    @Test
    @DisplayName("聚合 - 设计器形状：groupBy 数组 + aggregations 数组，按分组求和")
    void should_groupAndSum_when_designerShape() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupBy", Arrays.asList("region"));
        config.put("aggregations", Arrays.asList(
                aggregation("pay_amount", "sum", "total")));

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(2);
        Map<String, Object> huadong = findRow(result.getOutputData(), "region", "华东");
        assertThat(huadong.get("total")).isEqualTo(150.0);
    }

    @Test
    @DisplayName("聚合 - 演示数据形状：groupBy 逗号串 + agg 简写")
    void should_supportDemoShape_when_aggShorthandAndCommaString() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupBy", "region");
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("SUM(pay_amount)", "amount");
        config.put("agg", agg);

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(findRow(result.getOutputData(), "region", "华南").get("amount")).isEqualTo(30.0);
    }

    @Test
    @DisplayName("聚合 - 不分组时整表聚成一行")
    void should_aggregateWholeTable_when_noGroupBy() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aggregations", Arrays.asList(
                aggregation("pay_amount", "avg", "avg_amount")));

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.getOutputData()).hasSize(1);
        assertThat(result.getOutputData().get(0).get("avg_amount"))
                .isEqualTo((100.0 + 50.0 + 30.0) / 3);
    }

    @Test
    @DisplayName("聚合 - COUNT(*) 数的是整组行数，不需要对应列名")
    void should_countRows_when_fieldIsStar() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupBy", Arrays.asList("region"));
        config.put("aggregations", Arrays.asList(
                aggregation("*", "count", "cnt")));

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(findRow(result.getOutputData(), "region", "华东").get("cnt")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("聚合 - 表达式分组字段明确报错，而不是把全表聚成一行")
    void should_fail_when_groupByIsExpression() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupBy", Arrays.asList("DATE(created_at)"));
        config.put("aggregations", Arrays.asList(
                aggregation("pay_amount", "sum", "total")));

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("分组字段名不合法");
    }

    @Test
    @DisplayName("聚合 - 用到的列不在上游数据里时报出列名与上游列")
    void should_fail_when_columnMissingFromUpstream() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aggregations", Arrays.asList(
                aggregation("pay_amout", "sum", "total")));

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage())
                .contains("pay_amout")
                .contains("上游列: region, pay_amount");
    }

    @Test
    @DisplayName("聚合 - 认不了的汇总写法报错而不是猜")
    void should_fail_when_aggShorthandUnparsable() {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("总金额", "amount");
        config.put("agg", agg);

        NodeResult result = operator.execute(context(config, orders()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("无法识别的汇总写法");
    }

    @Test
    @DisplayName("聚合 - 没有任何汇总项时明确失败，不返回空表冒充成功")
    void should_fail_when_noAggregationsConfigured() {
        NodeResult result = operator.execute(context(
                new LinkedHashMap<String, Object>(), orders()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("没有汇总项");
    }

    @Test
    @DisplayName("聚合 - 上游为空时返回 0 行成功")
    void should_returnEmptySuccess_when_inputEmpty() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aggregations", Arrays.asList(
                aggregation("pay_amount", "sum", "total")));

        NodeResult result = operator.execute(
                context(config, new ArrayList<Map<String, Object>>()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isZero();
    }

    private Map<String, Object> findRow(List<Map<String, Object>> rows, String key, Object value) {
        for (Map<String, Object> row : rows) {
            if (value.equals(row.get(key))) {
                return row;
            }
        }
        throw new AssertionError("结果里没有 " + key + "=" + value + " 这一行");
    }
}
