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
 * Config parameters:
 * - field: String - field name to filter on
 * - operator: String - comparison operator (eq, ne, gt, lt, gte, lte, contains, in)
 * - value: Object - value to compare against
 * - logic: String - AND/OR when combining multiple conditions
 * - conditions: List - multiple filter conditions
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

        String field = context.getConfigString("field");
        String operator = context.getConfigString("operator");
        Object value = context.getConfigValue("value");

        if (field == null || operator == null) {
            return NodeResult.failure("field and operator are required for Filter operator");
        }

        List<Map<String, Object>> filtered = inputData.stream()
                .filter(row -> evaluateCondition(row, field, operator, value))
                .collect(Collectors.toList());

        long duration = System.currentTimeMillis() - start;
        log.info("Filter: {}/{} rows passed", filtered.size(), inputData.size());
        return NodeResult.success(filtered, filtered.size(), duration);
    }

    @Override
    public String getType() {
        return "filter";
    }

    private boolean evaluateCondition(Map<String, Object> row, String field, String operator, Object value) {
        Object fieldValue = row.get(field);
        if (fieldValue == null) return false;

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
