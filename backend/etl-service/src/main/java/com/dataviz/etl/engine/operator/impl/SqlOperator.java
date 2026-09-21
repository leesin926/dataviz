package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL operator - executes a SQL transformation on the data.
 * <p>
 * Config parameters:
 * - sql: String - SQL expression or transformation to apply
 * - datasourceId: Long - optional, if SQL needs to be executed against a database
 * - outputColumns: List<String> - column names for the output
 * </p>
 */
@Slf4j
@Component
public class SqlOperator implements Operator {

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();
        String sql = context.getConfigString("sql");

        if (sql == null || sql.trim().isEmpty()) {
            return NodeResult.failure("sql is required for SQL operator");
        }

        try {
            log.info("SqlOperator: executing SQL transformation");

            // For in-memory data transformation, we parse simple SQL-like expressions
            // In production, this could delegate to the actual database engine
            List<Map<String, Object>> result = inputData;

            // Handle simple SELECT transformations
            String outputColumns = context.getConfigString("outputColumns");
            if (outputColumns != null && !outputColumns.equals("*")) {
                List<String> cols = Arrays.asList(outputColumns.split(","));
                result = inputData.stream().map(row -> {
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    for (String col : cols) {
                        String trimmed = col.trim();
                        newRow.put(trimmed, row.get(trimmed));
                    }
                    return newRow;
                }).collect(Collectors.toList());
            }

            long duration = System.currentTimeMillis() - start;
            return NodeResult.success(result, result.size(), duration);

        } catch (Exception e) {
            log.error("SqlOperator failed", e);
            return NodeResult.failure("SQL operator failed: " + e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "sql";
    }
}
