package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregate operator - performs aggregation on data.
 * <p>
 * Config parameters:
 * - groupByFields: List<String> - fields to group by
 * - aggregations: List<Map> - each with field, function (sum, avg, count, min, max)
 * </p>
 */
@Slf4j
@Component
public class AggregateOperator implements Operator {

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();

        if (inputData == null || inputData.isEmpty()) {
            return NodeResult.success(new ArrayList<>(), 0, System.currentTimeMillis() - start);
        }

        String groupByFieldsStr = context.getConfigString("groupByFields");
        List<String> groupByFields = groupByFieldsStr != null
                ? Arrays.asList(groupByFieldsStr.split(","))
                : new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, String>> aggregations = (List<Map<String, String>>) context.getConfigValue("aggregations");

        if (aggregations == null || aggregations.isEmpty()) {
            return NodeResult.failure("aggregations config is required");
        }

        try {
            // Group data
            Map<String, List<Map<String, Object>>> groups;
            if (groupByFields.isEmpty()) {
                Map<String, List<Map<String, Object>>> tempGroups = new HashMap<>();
                tempGroups.put("__all__", inputData);
                groups = tempGroups;
            } else {
                groups = inputData.stream().collect(Collectors.groupingBy(row ->
                        groupByFields.stream()
                                .map(f -> String.valueOf(row.get(f.trim())))
                                .collect(Collectors.joining("|"))
                ));
            }

            // Aggregate each group
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
                Map<String, Object> outputRow = new LinkedHashMap<>();
                List<Map<String, Object>> groupRows = entry.getValue();

                // Add group by fields
                if (!groupByFields.isEmpty() && !groupRows.isEmpty()) {
                    for (String field : groupByFields) {
                        outputRow.put(field.trim(), groupRows.get(0).get(field.trim()));
                    }
                }

                // Compute aggregations
                for (Map<String, String> agg : aggregations) {
                    String field = agg.get("field");
                    String function = agg.get("function").toLowerCase();
                    String alias = agg.getOrDefault("alias", function + "_" + field);

                    double value = computeAggregation(groupRows, field, function);
                    outputRow.put(alias, value);
                }

                result.add(outputRow);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Aggregate: {} groups from {} rows", result.size(), inputData.size());
            return NodeResult.success(result, result.size(), duration);

        } catch (Exception e) {
            log.error("AggregateOperator failed", e);
            return NodeResult.failure("Aggregate operator failed: " + e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "aggregate";
    }

    private double computeAggregation(List<Map<String, Object>> rows, String field, String function) {
        List<Double> values = rows.stream()
                .map(row -> {
                    Object val = row.get(field);
                    if (val == null) return null;
                    try {
                        return Double.parseDouble(val.toString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (values.isEmpty()) return 0.0;

        double result;
        switch (function) {
            case "sum":
                result = values.stream().mapToDouble(Double::doubleValue).sum();
                break;
            case "avg":
                result = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                break;
            case "count":
                result = values.size();
                break;
            case "min":
                result = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                break;
            case "max":
                result = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                break;
            default:
                result = 0.0;
                break;
        }
        return result;
    }
}
