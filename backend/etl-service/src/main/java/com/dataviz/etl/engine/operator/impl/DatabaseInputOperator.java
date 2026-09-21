package com.dataviz.etl.engine.operator.impl;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Database input operator - reads data from a database table.
 * <p>
 * Config parameters:
 * - datasourceId: Long - the datasource to connect to
 * - tableName: String - table to read from
 * - columns: List<String> - columns to select (default: all)
 * - whereClause: String - optional WHERE condition
 * - limit: Integer - max rows to read
 * </p>
 */
@Slf4j
@Component
public class DatabaseInputOperator implements Operator {

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();

        String datasourceId = context.getConfigString("datasourceId");
        String tableName = context.getConfigString("tableName");
        Integer limit = context.getConfigInt("limit");

        if (tableName == null) {
            return NodeResult.failure("tableName is required for DatabaseInput operator");
        }

        try {
            // In production, this would use the datasource-service via Feign to get a connection
            // For now, we simulate the data read
            log.info("DatabaseInput: reading from table={}, datasource={}", tableName, datasourceId);

            // Build SQL
            String columns = context.getConfigString("columns");
            String selectColumns = (columns != null) ? columns : "*";
            String sql = "SELECT " + selectColumns + " FROM " + tableName;

            String whereClause = context.getConfigString("whereClause");
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                sql += " WHERE " + whereClause;
            }
            if (limit != null) {
                sql += " LIMIT " + limit;
            }

            // Simulated result - in production this would execute the actual query
            List<Map<String, Object>> data = new ArrayList<>();
            log.info("DatabaseInput: SQL={}", sql);

            long duration = System.currentTimeMillis() - start;
            return NodeResult.success(data, data.size(), duration);

        } catch (Exception e) {
            log.error("DatabaseInput operator failed", e);
            return NodeResult.failure("DatabaseInput failed: " + e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "database_input";
    }
}
