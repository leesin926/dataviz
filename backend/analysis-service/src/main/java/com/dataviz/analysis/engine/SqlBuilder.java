package com.dataviz.analysis.engine;

import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL builder that converts visual analysis configuration into SQL queries.
 * <p>
 * Handles dimensions (GROUP BY), metrics (aggregations), filters (WHERE/HAVING),
 * and ordering (ORDER BY) from the AnalysisQueryDTO configuration.
 * </p>
 * <p>
 * 这里产出的是<b>整条拼好的 SQL 字符串</b>，要经 HTTP 交给 datasource-service 执行，没有参数绑定可退，
 * 所以每个进入 SQL 的片段都必须是白名单结果：操作符、聚合函数、标识符、字面量各自收口，
 * 不认识的一律拒绝而不是原样透传（原先未知操作符走 else 分支直接拼接，等于开放注入口）。
 * </p>
 */
@Slf4j
@Component
public class SqlBuilder {

    /** 查询没带 limit 时的默认行数，与 datasource-service 的默认值对齐，避免 SQL 文本与实际行数不一致 */
    static final int DEFAULT_LIMIT = 1000;

    /** 服务端还会再夹一次，这里只是让生成的 SQL 自己就说得通 */
    static final int MAX_LIMIT = 5000;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

    /** 前端 FilterOperator 用符号（'='、'notIn'），后端 DTO 历史上用词形（eq、gte），两套都收进白名单 */
    private static final Map<String, String> OPERATORS = new HashMap<>();
    private static final List<String> COMPARISON_ONLY = Arrays.asList("=", "<>", ">", "<", ">=", "<=");

    /** 允许出现在 SELECT/HAVING 里的聚合函数；distinct 单独展开成 COUNT(DISTINCT x) */
    private static final Map<String, String> AGGREGATIONS = new HashMap<>();

    static {
        OPERATORS.put("=", "=");
        OPERATORS.put("eq", "=");
        OPERATORS.put("!=", "<>");
        OPERATORS.put("ne", "<>");
        OPERATORS.put("<>", "<>");
        OPERATORS.put(">", ">");
        OPERATORS.put("gt", ">");
        OPERATORS.put(">=", ">=");
        OPERATORS.put("gte", ">=");
        OPERATORS.put("<", "<");
        OPERATORS.put("lt", "<");
        OPERATORS.put("<=", "<=");
        OPERATORS.put("lte", "<=");
        OPERATORS.put("like", "LIKE");
        OPERATORS.put("notlike", "NOT LIKE");
        OPERATORS.put("in", "IN");
        OPERATORS.put("notin", "NOT IN");
        OPERATORS.put("between", "BETWEEN");
        OPERATORS.put("notbetween", "NOT BETWEEN");
        OPERATORS.put("isnull", "IS NULL");
        OPERATORS.put("isnotnull", "IS NOT NULL");

        AGGREGATIONS.put("sum", "SUM");
        AGGREGATIONS.put("avg", "AVG");
        AGGREGATIONS.put("count", "COUNT");
        AGGREGATIONS.put("max", "MAX");
        AGGREGATIONS.put("min", "MIN");
    }

    /**
     * Build a SQL query from the visual analysis configuration against the dataset's real source.
     */
    public String buildSql(AnalysisQueryDTO queryDTO, DatasetMeta meta) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(buildSelectClause(queryDTO));
        sql.append(" FROM ").append(resolveFrom(meta));

        String whereClause = buildWhereClause(queryDTO);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        String groupByClause = buildGroupByClause(queryDTO);
        if (!groupByClause.isEmpty()) {
            sql.append(" GROUP BY ").append(groupByClause);
        }

        String havingClause = buildHavingClause(queryDTO);
        if (!havingClause.isEmpty()) {
            sql.append(" HAVING ").append(havingClause);
        }

        String orderByClause = buildOrderByClause(queryDTO);
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        sql.append(" LIMIT ").append(resolveLimit(queryDTO));

        return sql.toString();
    }

    private String buildSelectClause(AnalysisQueryDTO queryDTO) {
        StringBuilder select = new StringBuilder();

        if (queryDTO.getDimensions() != null && !queryDTO.getDimensions().isEmpty()) {
            String dims = queryDTO.getDimensions().stream()
                    .map(d -> column(d.getField(), "维度字段"))
                    .collect(Collectors.joining(", "));
            select.append(dims);
        }

        if (queryDTO.getMetrics() != null && !queryDTO.getMetrics().isEmpty()) {
            if (select.length() != 0) {
                select.append(", ");
            }
            String metrics = queryDTO.getMetrics().stream()
                    .map(m -> aggregationExpression(m.getField(), m.getAggFunction(), m.getAlias()))
                    .collect(Collectors.joining(", "));
            select.append(metrics);
        }

        if (select.length() == 0) {
            select.append("*");
        }
        return select.toString();
    }

    private String aggregationExpression(String field, String aggFunction, String alias) {
        String func = aggFunction == null ? "SUM" : aggFunction.trim().toUpperCase();
        String expression = aggregationOf(func, column(field, "指标字段"));
        return expression + " AS " + aliasColumn(alias, field, func);
    }

    private String aggregationOf(String func, String column) {
        if ("DISTINCT".equals(func) || "COUNTDISTINCT".equals(func) || "COUNT_DISTINCT".equals(func)) {
            return "COUNT(DISTINCT " + column + ")";
        }
        String allowed = AGGREGATIONS.get(func.toLowerCase());
        if (allowed == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的聚合函数: " + func);
        }
        return allowed + "(" + column + ")";
    }

    private String aliasColumn(String alias, String field, String func) {
        String cleaned = alias == null ? "" : alias.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.isEmpty()) {
            cleaned = func.toLowerCase().replace("_", "") + "_" + field.replaceAll("[^a-zA-Z0-9_]", "");
        }
        if (cleaned.isEmpty() || "_".equals(cleaned)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "指标别名与字段名均无法作为列名使用");
        }
        return "`" + cleaned + "`";
    }

    private String buildWhereClause(AnalysisQueryDTO queryDTO) {
        if (queryDTO.getFilters() == null || queryDTO.getFilters().isEmpty()) {
            return "";
        }
        return queryDTO.getFilters().stream()
                .map(f -> predicate(column(f.getField(), "过滤字段"),
                        canonicalOperator(f.getOperator()), f.getValue()))
                .collect(Collectors.joining(" AND "));
    }

    private String predicate(String field, String operator, Object value) {
        if ("IS NULL".equals(operator) || "IS NOT NULL".equals(operator)) {
            return field + " " + operator;
        }
        if ("IN".equals(operator) || "NOT IN".equals(operator)) {
            List<?> values = asList(value, operator);
            String literals = values.stream().map(this::literal).collect(Collectors.joining(", "));
            return field + " " + operator + " (" + literals + ")";
        }
        if ("BETWEEN".equals(operator) || "NOT BETWEEN".equals(operator)) {
            List<?> values = asList(value, operator);
            if (values.size() != 2) {
                throw new BizException(ErrorCode.BAD_REQUEST, operator + " 需要两个边界值");
            }
            return field + " " + operator + " " + literal(values.get(0)) + " AND " + literal(values.get(1));
        }
        if ("LIKE".equals(operator) || "NOT LIKE".equals(operator)) {
            return field + " " + operator + " '%" + likeLiteral(value) + "%'";
        }
        return field + " " + operator + " " + literal(value);
    }

    private String canonicalOperator(String rawOperator) {
        if (rawOperator == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "过滤条件缺少操作符");
        }
        String key = rawOperator.trim().toLowerCase().replace("_", "").replace(" ", "");
        String operator = OPERATORS.get(key);
        if (operator == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的过滤操作符: " + rawOperator);
        }
        return operator;
    }

    private String buildGroupByClause(AnalysisQueryDTO queryDTO) {
        if (queryDTO.getDimensions() == null || queryDTO.getDimensions().isEmpty()) {
            return "";
        }
        return queryDTO.getDimensions().stream()
                .map(d -> column(d.getField(), "维度字段"))
                .collect(Collectors.joining(", "));
    }

    private String buildHavingClause(AnalysisQueryDTO queryDTO) {
        if (queryDTO.getMetricFilters() == null || queryDTO.getMetricFilters().isEmpty()) {
            return "";
        }
        return queryDTO.getMetricFilters().stream()
                .map(f -> {
                    String func = f.getAggFunction() == null ? "SUM" : f.getAggFunction().trim().toUpperCase();
                    String operator = canonicalOperator(f.getOperator());
                    if (!COMPARISON_ONLY.contains(operator)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "指标过滤只支持数值比较，收到: " + operator);
                    }
                    return aggregationOf(func, column(f.getField(), "指标字段")) + " " + operator + " "
                            + numericLiteral(f.getValue());
                })
                .collect(Collectors.joining(" AND "));
    }

    private String buildOrderByClause(AnalysisQueryDTO queryDTO) {
        if (queryDTO.getOrders() == null || queryDTO.getOrders().isEmpty()) {
            return "";
        }
        return queryDTO.getOrders().stream()
                .map(o -> {
                    String direction = o.getDirection() == null ? "ASC" : o.getDirection().trim().toUpperCase();
                    if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "不支持的排序方向: " + o.getDirection());
                    }
                    return column(o.getField(), "排序字段") + " " + direction;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * 数据集的真实来源：自定义 SQL 包成子查询，物理表名按标识符白名单校验后使用。
     * <p>原先这里是拼造的 {@code dataset_<id>}，那张表并不存在，所以查询永远返回空集。</p>
     */
    private String resolveFrom(DatasetMeta meta) {
        if (meta == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在，无法确定查询来源");
        }
        String sqlQuery = trimToEmpty(meta.getSqlQuery());
        if (!sqlQuery.isEmpty()) {
            String single = sqlQuery.replaceAll(";\\s*$", "").trim();
            if (single.contains(";")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 含多条语句，拒绝作为查询来源");
            }
            if (!single.toUpperCase().startsWith("SELECT")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 不是只读查询，拒绝作为查询来源");
            }
            return "(" + single + ") t_source";
        }
        String tableName = trimToEmpty(meta.getTableName());
        if (!tableName.isEmpty()) {
            if (!IDENTIFIER.matcher(tableName).matches()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集表名不是合法标识符: " + tableName);
            }
            return "`" + tableName + "`";
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "数据集未配置表名或自定义 SQL，无法执行查询");
    }

    private int resolveLimit(AnalysisQueryDTO queryDTO) {
        Integer limit = queryDTO.getLimit();
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String column(String raw, String label) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "名无效: " + raw);
        }
        return "`" + cleaned + "`";
    }

    private List<?> asList(Object value, String operator) {
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, operator + " 的取值列表不能为空");
            }
            return Arrays.asList(collection.toArray());
        }
        if (value instanceof String) {
            String[] parts = ((String) value).split(",");
            if (parts.length > 1) {
                return Arrays.asList(parts);
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, operator + " 需要一个数组作为取值");
    }

    private String literal(Object value) {
        if (value == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "过滤值不能为空");
        }
        if (value instanceof Number) {
            return numericLiteral(value);
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "1" : "0";
        }
        if (value instanceof Collection) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该操作符的取值不接受数组");
        }
        return "'" + escapeString(String.valueOf(value)) + "'";
    }

    /** LIKE 里的 % 与 _ 是通配符，用户输入带它们时按字面匹配更符合直觉 */
    private String likeLiteral(Object value) {
        String escaped = escapeString(String.valueOf(value));
        return escaped.replace("%", "\\%").replace("_", "\\_");
    }

    private String numericLiteral(Object value) {
        if (value instanceof Number) {
            double asDouble = ((Number) value).doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "过滤值必须是有限数值");
            }
            return value.toString();
        }
        String text = trimToEmpty(value == null ? null : String.valueOf(value));
        if (!NUMBER.matcher(text).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该过滤条件要求数值，收到: " + text);
        }
        return text;
    }

    /**
     * MySQL 默认模式下反斜杠也是转义符，所以先倍化反斜杠再倍化单引号；
     * 含 NUL 或换行以外控制字符的取值直接拒绝，避免把转义边界带进字面量。
     */
    private String escapeString(String value) {
        if (value.length() > 1024) {
            throw new BizException(ErrorCode.BAD_REQUEST, "过滤值过长（上限 1024 字符）");
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\0' || (c < ' ' && c != '\t')) {
                throw new BizException(ErrorCode.BAD_REQUEST, "过滤值含非法控制字符");
            }
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\'') {
                out.append("''");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
