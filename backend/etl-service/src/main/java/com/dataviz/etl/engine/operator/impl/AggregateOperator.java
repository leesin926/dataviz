package com.dataviz.etl.engine.operator.impl;

import com.dataviz.common.core.sql.SqlIdentifiers;
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
 * Config parameters (两种形状都接):
 * - groupBy: List&lt;String&gt; | "a,b" - 设计器用这个；也兼容 groupByFields
 * - aggregations: List of {field, function, alias} - 设计器用这个
 * - agg: Map of "FUNC(col)" -&gt; alias - 演示数据里的简写
 * 分组字段只认普通列名：DATE(created_at) 这类表达式要的是"按天分桶"，那是 SQL 侧的能力，
 * 在这里当列名取值只会每行都取到 null，然后把全表聚成一行。宁可明确报错。
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

        List<String> groupByFields;
        List<Map<String, String>> aggregations;
        try {
            groupByFields = readGroupBy(context);
            aggregations = readAggregations(context);
        } catch (RuntimeException e) {
            return NodeResult.failure("聚合节点配置不可用: " + e.getMessage());
        }

        if (aggregations.isEmpty()) {
            return NodeResult.failure("聚合节点没有汇总项：aggregations 里每条要有 field/function（或配 agg）");
        }

        List<String> missing = missingColumns(inputData.get(0).keySet(), groupByFields, aggregations);
        if (!missing.isEmpty()) {
            return NodeResult.failure("聚合节点用到的列不在上游数据里: " + String.join(", ", missing)
                    + "（上游列: " + String.join(", ", inputData.get(0).keySet()) + "）");
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

                    double value = "count".equals(function) && isRowCount(field)
                            ? groupRows.size()
                            : computeAggregation(groupRows, field, function);
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

    private List<String> readGroupBy(NodeContext context) {
        List<String> raw = context.getConfigList("groupBy");
        if (raw.isEmpty()) {
            raw = context.getConfigList("groupByFields");
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String field : raw) {
            out.add(SqlIdentifiers.require(field, "分组字段"));
        }
        return out;
    }

    private List<Map<String, String>> readAggregations(NodeContext context) {
        List<Map<String, String>> out = new ArrayList<>();
        Object configured = context.getConfigValue("aggregations");
        if (configured instanceof Collection<?>) {
            for (Object item : (Collection<?>) configured) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) item;
                Object field = map.get("field");
                Object function = map.get("function");
                if (field == null || function == null) {
                    continue;
                }
                String fieldName = field.toString().trim();
                String functionName = function.toString().trim().toLowerCase(Locale.ROOT);
                Object alias = map.get("alias");
                String aliasName = alias == null || alias.toString().trim().isEmpty()
                        ? functionName + "_" + fieldName : alias.toString().trim();
                Map<String, String> agg = new LinkedHashMap<>();
                agg.put("field", fieldName);
                agg.put("function", functionName);
                agg.put("alias", aliasName);
                out.add(agg);
            }
        }
        return out.isEmpty() ? parseAggShorthand(context.getConfigValue("agg")) : out;
    }

    /**
     * 演示数据里的 {"SUM(pay_amount)": "amount"} 形状：括号左边是函数，括号里是列名。
     * 认不了就抛 —— 这里"猜"成整组计数，症状是汇总值差好几个数量级却一路成功写进目标表。
     */
    private List<Map<String, String>> parseAggShorthand(Object raw) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(raw instanceof Map<?, ?>)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String expression = entry.getKey() == null ? "" : entry.getKey().toString().trim();
            int open = expression.indexOf('(');
            int close = expression.lastIndexOf(')');
            if (open <= 0 || close < open) {
                throw new IllegalArgumentException("无法识别的汇总写法: " + expression
                        + "（应为 FUNC(列名)，如 SUM(pay_amount)）");
            }
            String functionName = expression.substring(0, open).trim().toLowerCase(Locale.ROOT);
            String fieldName = expression.substring(open + 1, close).trim();
            Map<String, String> agg = new LinkedHashMap<>();
            agg.put("field", fieldName);
            agg.put("function", functionName);
            agg.put("alias", entry.getValue() == null
                    ? functionName + "_" + fieldName : entry.getValue().toString().trim());
            out.add(agg);
        }
        return out;
    }

    /** COUNT(1) / COUNT(*) 数的是整组行数，没有对应列名。 */
    private boolean isRowCount(String field) {
        return "*".equals(field) || "1".equals(field);
    }

    /**
     * 用到的列在上游数据里不存在时返回这些列名。
     * 不查的话症状是「聚合结果为 0 但任务成功」——computeAggregation 取不到值就返回 0.0，
     * 写进目标表之后没人会回头怀疑列名拼错了。
     */
    private List<String> missingColumns(Set<String> available,
                                        List<String> groupByFields,
                                        List<Map<String, String>> aggregations) {
        List<String> missing = new ArrayList<>();
        for (String field : groupByFields) {
            if (!available.contains(field) && !missing.contains(field)) {
                missing.add(field);
            }
        }
        for (Map<String, String> agg : aggregations) {
            String field = agg.get("field");
            if (field == null || isRowCount(field)) {
                continue;
            }
            if (!available.contains(field) && !missing.contains(field)) {
                missing.add(field);
            }
        }
        return missing;
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
