package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Filter operator - filters rows based on conditions.
 * <p>
 * Config parameters (两种形状都接，语义一致：条件之间 AND):
 * - filters / conditions: List of {field, operator, value} - 设计器与演示数据用这个
 * - field + operator + value: 单个条件，早期算子表单用这个
 * - logic: "OR" 时条件之间取并集，默认 AND
 * </p>
 */
@Slf4j
@Component
public class FilterOperator implements Operator {

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();

        if (inputData == null || inputData.isEmpty()) {
            return NodeResult.success(new ArrayList<>(), 0, System.currentTimeMillis() - start);
        }

        List<Condition> conditions = readConditions(context);
        if (conditions.isEmpty()) {
            return NodeResult.failure("过滤节点没有可用条件：filters 里每条要有 field/operator/value");
        }

        boolean orLogic = "OR".equalsIgnoreCase(context.getConfigString("logic"));

        List<Map<String, Object>> filtered = inputData.stream()
                .filter(row -> matches(row, conditions, orLogic))
                .collect(Collectors.toList());

        long duration = System.currentTimeMillis() - start;
        log.info("Filter: {}/{} rows passed ({} conditions, {})",
                filtered.size(), inputData.size(), conditions.size(), orLogic ? "OR" : "AND");
        return NodeResult.success(filtered, filtered.size(), duration);
    }

    private boolean matches(Map<String, Object> row, List<Condition> conditions, boolean orLogic) {
        for (Condition condition : conditions) {
            boolean passed = evaluateCondition(row, condition.field, condition.operator, condition.value);
            if (orLogic && passed) {
                return true;
            }
            if (!orLogic && !passed) {
                return false;
            }
        }
        return !orLogic;
    }

    /**
     * 收 filters / conditions 数组，收不到再退回单条件三件套。
     * <p>
     * 退回去不是为兼容而兼容：库里已经存着早期表单形状写出来的 transform_config，
     * 只认数组的话这些任务是"点启动就报没有可用条件"，而设计器里那个节点看着一切正常。
     */
    private List<Condition> readConditions(NodeContext context) {
        List<Condition> out = new ArrayList<>();
        Object configured = context.getConfigValue("filters");
        if (!(configured instanceof Collection<?>)) {
            configured = context.getConfigValue("conditions");
        }
        if (configured instanceof Collection<?>) {
            for (Object item : (Collection<?>) configured) {
                Condition condition = toCondition(item);
                if (condition != null) {
                    out.add(condition);
                }
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        Condition single = toCondition(context.getConfig());
        if (single != null) {
            out.add(single);
        }
        return out;
    }

    private Condition toCondition(Object raw) {
        if (!(raw instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) raw;
        Object field = map.get("field");
        Object operator = map.get("operator");
        if (field == null || operator == null) {
            return null;
        }
        return new Condition(field.toString().trim(), operator.toString().trim(), map.get("value"));
    }

    private static final class Condition {
        private final String field;
        private final String operator;
        private final Object value;

        private Condition(String field, String operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }

    @Override
    public String getType() {
        return "filter";
    }

    private boolean evaluateCondition(Map<String, Object> row, String field, String operator, Object value) {
        Object fieldValue = row.get(field);
        if (fieldValue == null) return false;
        // 条件没填值 ⇒ 判不了。按"不通过"处理：这里放开的话，一条手滑的空白条件会变成少删数据
        if (value == null) return false;

        String opLower = operator.toLowerCase();
        if ("eq".equals(opLower) || "==".equals(opLower)) {
            return Objects.equals(fieldValue.toString(), value.toString());
        } else if ("ne".equals(opLower) || "!=".equals(opLower)) {
            return !Objects.equals(fieldValue.toString(), value.toString());
        } else if ("gt".equals(opLower) || ">".equals(opLower)) {
            return compareValues(fieldValue, value) > 0;
        } else if ("lt".equals(opLower) || "<".equals(opLower)) {
            return compareValues(fieldValue, value) < 0;
        } else if ("gte".equals(opLower) || ">=".equals(opLower)) {
            return compareValues(fieldValue, value) >= 0;
        } else if ("lte".equals(opLower) || "<=".equals(opLower)) {
            return compareValues(fieldValue, value) <= 0;
        } else if ("contains".equals(opLower)) {
            return fieldValue.toString().contains(value.toString());
        } else if ("in".equals(opLower) && value instanceof List<?>) {
            List<?> list = (List<?>) value;
            return list.contains(fieldValue);
        } else {
            return false;
        }
    }

    private int compareValues(Object a, Object b) {
        try {
            double da = Double.parseDouble(a.toString());
            double db = Double.parseDouble(b.toString());
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.toString().compareTo(b.toString());
        }
    }
}
