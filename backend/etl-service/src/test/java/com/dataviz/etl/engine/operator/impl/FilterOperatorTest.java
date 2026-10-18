package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 过滤节点的配置形状与判定语义。
 * <p>
 * 库里既有设计器写出的 filters 数组，也有早期单条件表单写出的 field/operator/value。
 * 只认一种的话，那些任务是"点启动就说没有可用条件"，而设计器里节点看着一切正常。
 */
class FilterOperatorTest {

    private final FilterOperator operator = new FilterOperator();

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private NodeContext context(Map<String, Object> config, List<Map<String, Object>> input) {
        return NodeContext.builder()
                .nodeId("filter-1")
                .operatorType("filter")
                .config(config)
                .inputData(input)
                .build();
    }

    private List<Map<String, Object>> sampleOrders() {
        return Arrays.asList(
                row("status", "paid", "amount", 120.0),
                row("status", "cancelled", "amount", 30.0),
                row("status", "paid", "amount", 5.0));
    }

    private Map<String, Object> condition(String field, String operatorName, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field);
        map.put("operator", operatorName);
        map.put("value", value);
        return map;
    }

    @Test
    @DisplayName("过滤 - filters 数组里的 != 条件按演示数据形状生效")
    void should_applyNotEquals_when_conditionsGivenAsArray() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Collections.singletonList(
                condition("status", "!=", "cancelled")));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.getOutputData()).extracting("status").containsExactly("paid", "paid");
    }

    @Test
    @DisplayName("过滤 - 多条条件默认 AND：只留下同时满足的行")
    void should_andConditions_when_logicNotGiven() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Arrays.asList(
                condition("status", "==", "paid"),
                condition("amount", ">", 50)));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.getOutputData()).hasSize(1);
        assertThat(result.getOutputData().get(0).get("amount")).isEqualTo(120.0);
    }

    @Test
    @DisplayName("过滤 - logic=OR 时条件取并集")
    void should_orConditions_when_logicIsOr() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("logic", "OR");
        config.put("filters", Arrays.asList(
                condition("status", "==", "cancelled"),
                condition("amount", ">", 100)));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.getOutputData()).hasSize(2);
    }

    @Test
    @DisplayName("过滤 - 早期单条件表单形状仍可用")
    void should_fallBackToSingleCondition_when_filtersArrayMissing() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("field", "status");
        config.put("operator", "eq");
        config.put("value", "paid");

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("过滤 - 条件没填值时该行判为不通过：空白条件不能悄悄放行全表")
    void should_rejectRow_when_conditionValueMissing() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Collections.singletonList(
                condition("status", "eq", null)));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.getOutputData()).isEmpty();
    }

    @Test
    @DisplayName("过滤 - 没有任何可用条件时明确失败，而不是原样透传全表")
    void should_fail_when_noConditionsConfigured() {
        NodeResult result = operator.execute(context(
                new LinkedHashMap<String, Object>(), sampleOrders()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("没有可用条件");
    }

    @Test
    @DisplayName("过滤 - 上游为空时返回 0 行成功，不报错")
    void should_returnEmptySuccess_when_inputEmpty() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Collections.singletonList(
                condition("status", "eq", "paid")));

        NodeResult result = operator.execute(
                context(config, new ArrayList<Map<String, Object>>()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isZero();
    }

    @Test
    @DisplayName("过滤 - in 操作符按列表成员判断")
    void should_matchInList_when_operatorIsIn() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Collections.singletonList(
                condition("status", "in", Arrays.asList("paid", "refund"))));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.getOutputData()).hasSize(2);
    }

    @Test
    @DisplayName("过滤 - 未知操作符判为不通过，不猜语义")
    void should_reject_when_operatorUnknown() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("filters", Collections.singletonList(
                condition("status", "startsWith", "pa")));

        NodeResult result = operator.execute(context(config, sampleOrders()));

        assertThat(result.getOutputData()).isEmpty();
    }
}
