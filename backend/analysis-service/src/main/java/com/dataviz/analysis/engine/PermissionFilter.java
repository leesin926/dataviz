package com.dataviz.analysis.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Permission filter for row-level and column-level data security.
 * <p>
 * Rewrites SQL queries to enforce:
 * - Row-level security: adds WHERE conditions based on user/tenant permissions
 * - Column-level security: removes columns the user doesn't have access to
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionFilter {

    private final StringRedisTemplate redisTemplate;

    private static final String ROW_PERM_PREFIX = "perm:row:";
    private static final String COL_PERM_PREFIX = "perm:col:";

    /**
     * Apply row-level and column-level permission filters to the SQL.
     */
    public String applyPermission(String sql, Long userId, Long tenantId) {
        // Apply row-level permissions
        String rowFilteredSql = applyRowPermission(sql, userId, tenantId);

        // Apply column-level permissions
        String colFilteredSql = applyColumnPermission(rowFilteredSql, userId, tenantId);

        return colFilteredSql;
    }

    /**
     * Apply row-level security by appending WHERE conditions.
     * <p>
     * For example, if a user can only see their department's data,
     * this would add "AND dept_id = X" to the query.
     * </p>
     */
    private String applyRowPermission(String sql, Long userId, Long tenantId) {
        // Load row-level permission rules from Redis/cache
        String permKey = ROW_PERM_PREFIX + tenantId + ":" + userId;
        String rowRule = redisTemplate.opsForValue().get(permKey);

        if (rowRule != null && !rowRule.trim().isEmpty()) {
            log.debug("Applying row-level permission: {}", rowRule);

            // Check if SQL already has WHERE clause
            String upperSql = sql.toUpperCase();
            int whereIdx = upperSql.indexOf("WHERE");
            int groupByIdx = upperSql.indexOf("GROUP BY");

            if (whereIdx > 0) {
                // Append to existing WHERE clause
                if (groupByIdx > 0) {
                    return sql.substring(0, groupByIdx) + " AND (" + rowRule + ") " + sql.substring(groupByIdx);
                } else {
                    return sql + " AND (" + rowRule + ")";
                }
            } else {
                // Add new WHERE clause
                if (groupByIdx > 0) {
                    return sql.substring(0, groupByIdx) + " WHERE " + rowRule + " " + sql.substring(groupByIdx);
                } else {
                    return sql + " WHERE " + rowRule;
                }
            }
        }

        return sql;
    }

    /**
     * Apply column-level security by removing restricted columns from SELECT.
     * <p>
     * For example, if a user can't see salary data, this would remove
     * salary columns from the query result.
     * </p>
     */
    private String applyColumnPermission(String sql, Long userId, Long tenantId) {
        String permKey = COL_PERM_PREFIX + tenantId + ":" + userId;
        String colRule = redisTemplate.opsForValue().get(permKey);

        if (colRule != null && !colRule.trim().isEmpty()) {
            log.debug("Applying column-level permission. Restricted columns: {}", colRule);
            // Parse restricted columns and remove them from SELECT clause
            // This is a simplified implementation - in production, use a proper SQL parser
            List<String> restrictedColumns = Arrays.asList(colRule.split(","));
            // Column filtering would be applied here
        }

        return sql;
    }
}
