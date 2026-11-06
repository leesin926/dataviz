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
import java.util.LinkedHashMap;
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
    /**
     * 维度的日期分桶粒度（API-113）。此前 {@code AnalysisQueryDTO.DimensionConfig.dateFormat} 在本类<b>零引用</b>
     * —— 界面上选"按月"，SQL 里却是按原始行分组，等于这个配置项被吞掉。
     * 只认下面两张表：粒度词与固定模式串；认不出来的值一律拒绝，而不是原样塞进 {@code DATE_FORMAT} 的参数位。
     */
    private static final Map<String, String> DATE_GRANULARITY = new HashMap<>();

    /** 前端常见的模式串写法（小写键），与粒度词等价 */
    private static final Map<String, String> DATE_PATTERNS = new HashMap<>();

    /**
     * 首词已是 SELECT、但仍会改变语句性质的写法（API-114）。
     * 只看首词就等于允许"读"数据集把结果写到服务端磁盘上或给行加排他锁。
     */
    private static final List<String> READ_ONLY_DENY = Arrays.asList(
            "INTO OUTFILE", "INTO DUMPFILE", "FOR UPDATE", "LOCK IN SHARE MODE");

    static {
        DATE_GRANULARITY.put("minute", "%Y-%m-%d %H:%i");
        DATE_GRANULARITY.put("hour", "%Y-%m-%d %H");
        DATE_GRANULARITY.put("day", "%Y-%m-%d");
        DATE_GRANULARITY.put("date", "%Y-%m-%d");
        DATE_GRANULARITY.put("week", "%x-W%v");
        DATE_GRANULARITY.put("month", "%Y-%m");
        DATE_GRANULARITY.put("quarter", "%Y-Q%q");
        DATE_GRANULARITY.put("year", "%Y");
        DATE_PATTERNS.put("yyyy", "%Y");
        DATE_PATTERNS.put("yyyy-mm", "%Y-%m");
        DATE_PATTERNS.put("yyyy-mm-dd", "%Y-%m-%d");
        DATE_PATTERNS.put("yyyy-mm-dd hh", "%Y-%m-%d %H");
        DATE_PATTERNS.put("yyyy-mm-dd hh:mm", "%Y-%m-%d %H:%i");
        DATE_PATTERNS.put("yyyy-mm-dd hh:mm:ss", "%Y-%m-%d %H:%i:%s");
    }

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
        return buildSql(queryDTO, meta, null, null);
    }

    /**
     * 同一条查询的分页形态，供"导出全量"逐页取数使用。
     * <p>
     * {@code rowLimit}/{@code rowOffset} 为 null 时沿用查询自带的 limit（交互查询路径的口径一字不改）。
     * 分页能骑在既有的 5000 行天花板<b>之内</b>：每一页都不超过天花板，总量靠 OFFSET 往前推，
     * 所以不需要把服务端天花板抬大 —— 天花板防的是"一次查询把库拖垮"，不是"导出总量"。
     */
    public String buildSql(AnalysisQueryDTO queryDTO, DatasetMeta meta, Integer rowLimit, Integer rowOffset) {
        return buildSql(queryDTO, meta, rowLimit, rowOffset, null);
    }

    /**
     * @param permissionPredicate 行级权限谓词（已由 {@link #permissionPredicate} 过白名单）。
     *                            它拼进 WHERE，因此必然在 GROUP BY / HAVING / ORDER BY / LIMIT <b>之前</b> ——
     *                            原先在成品 SQL 上做 {@code sql + " AND (...)"} 的写法会把谓词留在 LIMIT 之后，
     *                            规则一真配上就是语法错。
     */
    public String buildSql(AnalysisQueryDTO queryDTO, DatasetMeta meta, Integer rowLimit, Integer rowOffset,
                           String permissionPredicate) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(buildSelectClause(queryDTO));
        sql.append(" FROM ").append(resolveFrom(meta));

        String whereClause = buildWhereClause(queryDTO);
        String permission = trimToNull(permissionPredicate);
        if (permission != null) {
            whereClause = whereClause.isEmpty() ? "(" + permission + ")"
                    : whereClause + " AND (" + permission + ")";
        }
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

        sql.append(" LIMIT ").append(resolveLimit(queryDTO, rowLimit));

        if (rowOffset != null && rowOffset > 0) {
            sql.append(" OFFSET ").append(rowOffset);
        }

        return sql.toString();
    }

    /**
     * 行级权限规则用的就是这条白名单：字段名、操作符、字面量与业务 filters 走同一个收口。
     * <p>
     * 规则绝不能存成"管理员手写的一段 SQL"然后原样拼接 —— 那等于给配规则的人开了一个比查询本身更大的
     * 注入口（他能越过规则去读这个数据源能读的任何东西）。存结构化条件、渲染时过白名单，是唯一能证的地方。
     */
    public String permissionPredicate(List<AnalysisQueryDTO.FilterConfig> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }
        return conditions.stream()
                .map(f -> predicate(column(f.getField(), "权限字段"),
                        canonicalOperator(f.getOperator()), f.getValue()))
                .collect(Collectors.joining(" AND "));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildSelectClause(AnalysisQueryDTO queryDTO) {
        StringBuilder select = new StringBuilder();

        Map<String, String> dimensions = dimensionExpressions(queryDTO);
        if (!dimensions.isEmpty()) {
            select.append(dimensions.entrySet().stream()
                    .map(e -> e.getValue().equals(e.getKey())
                            ? e.getKey() : e.getValue() + " AS " + e.getKey())
                    .collect(Collectors.joining(", ")));
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
        Map<String, String> dimensions = dimensionExpressions(queryDTO);
        if (dimensions.isEmpty()) {
            return "";
        }
        // 分桶后必须按表达式分组：按裸列分组再投影 DATE_FORMAT，只_full_group_by 会把分桶结果判成"未在 GROUP BY 里"
        return dimensions.values().stream().collect(Collectors.joining(", "));
    }

    /**
     * 维度投影表：清洗后的列名 → 该列进入 SELECT/GROUP BY 的表达式。
     * <p>
     * 用 {@link LinkedHashMap} 而不是列表，是因为"同一字段 + 同一粒度"应当自然合并，
     * 而"同一字段 + 两种粒度"必须拒绝 —— 两者的投影别名同名，后者会把结果列覆盖掉，
     * 前端拿到的就是看不懂的错列（症状与 API-108 同族：接口 200、图上是错的形状）。
     */
    private Map<String, String> dimensionExpressions(AnalysisQueryDTO queryDTO) {
        Map<String, String> expressions = new LinkedHashMap<>();
        if (queryDTO.getDimensions() == null) {
            return expressions;
        }
        for (AnalysisQueryDTO.DimensionConfig d : queryDTO.getDimensions()) {
            String cleaned = column(d.getField(), "维度字段");
            String format = resolveDateFormat(d.getDateFormat());
            String expression = format == null ? cleaned : "DATE_FORMAT(" + cleaned + ", '" + format + "')";
            String previous = expressions.get(cleaned);
            if (previous != null && !previous.equals(expression)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "同一维度字段 " + d.getField() + " 配了两种日期粒度，投影列名会冲突");
            }
            expressions.put(cleaned, expression);
        }
        return expressions;
    }

    /** @return 进入 {@code DATE_FORMAT} 的模式串；粒度为空返回 null（不分桶），认不出来则拒绝 */
    private String resolveDateFormat(String dateFormat) {
        String raw = trimToNull(dateFormat);
        if (raw == null) {
            return null;
        }
        String key = raw.toLowerCase().replace("_", "-");
        String mysqlFormat = DATE_GRANULARITY.get(key);
        if (mysqlFormat == null) {
            mysqlFormat = DATE_PATTERNS.get(key);
        }
        if (mysqlFormat != null) {
            return mysqlFormat;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "不支持的日期粒度: " + dateFormat
                + "（可用：" + String.join(" / ", DATE_GRANULARITY.keySet()) + "，或 yyyy / yyyy-MM / yyyy-MM-dd / yyyy-MM-dd HH:mm:ss）");
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
        Map<String, String> metricAliases = metricAliases(queryDTO);
        return queryDTO.getOrders().stream()
                .map(o -> {
                    String direction = o.getDirection() == null ? "ASC" : o.getDirection().trim().toUpperCase();
                    if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "不支持的排序方向: " + o.getDirection());
                    }
                    // 排序命中指标时必须换成投影别名：裸列名在 GROUP BY 之后不是聚合值，
                    // only_full_group_by 下直接报错（只有维度恰好是唯一键时才侥幸通过，换一个数据集就崩）
                    String alias = o.getField() == null ? null : metricAliases.get(o.getField().trim().toLowerCase());
                    return (alias != null ? alias : column(o.getField(), "排序字段")) + " " + direction;
                })
                .collect(Collectors.joining(", "));
    }

    /** 字段名（小写）→ SELECT 中该指标的投影别名；同字段多聚合取第一个，与 SELECT 里的先后一致 */
    private Map<String, String> metricAliases(AnalysisQueryDTO queryDTO) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (queryDTO.getMetrics() == null) {
            return aliases;
        }
        for (AnalysisQueryDTO.MetricConfig m : queryDTO.getMetrics()) {
            if (m.getField() == null || m.getField().trim().isEmpty()) {
                continue;
            }
            String func = m.getAggFunction() == null ? "SUM" : m.getAggFunction().trim().toUpperCase();
            String key = m.getField().trim().toLowerCase();
            if (!aliases.containsKey(key)) {
                aliases.put(key, aliasColumn(m.getAlias(), m.getField(), func));
            }
        }
        return aliases;
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
            // 判分号与关键词时先掩掉字符串字面量与注释（API-115）：
            // 原先 single.contains(";") 会把 SELECT 'a;b' AS k 这种完全只读的写法判成多语句，
            // 症状与 API-108/109 同族 —— 接口 200、图上空框，看的人会去查数据集有没有数。
            String masked = maskLiteralsAndComments(single).toUpperCase();
            if (masked.contains(";")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 含多条语句，拒绝作为查询来源");
            }
            if (!masked.trim().startsWith("SELECT")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 不是只读查询，拒绝作为查询来源");
            }
            for (String banned : READ_ONLY_DENY) {
                if (masked.contains(banned)) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "数据集自定义 SQL 含 " + banned + "，已超出只读查询，拒绝作为查询来源");
                }
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

    /**
     * 把字符串字面量、反引号标识符与注释替换成占位符，只用于<b>校验</b>（返回值不进入 SQL）。
     * <p>
     * 掩掉之后，字面量里的 {@code ;}、{@code INTO OUTFILE}、{@code FOR UPDATE} 不再冒充语法，
     * 而注释也不能靠 {@code --} 把后面的守卫吃掉。反斜杠转义与 MySQL 的连续两个引号转义都按原样处理，
     * 否则一个 {@code 'it''s'} 就会让后半句逃脱检查。
     */
    static String maskLiteralsAndComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                boolean identifier = c == '`';
                i++;
                while (i < n) {
                    char d = sql.charAt(i);
                    if (!identifier && d == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (d == c) {
                        if (i + 1 < n && sql.charAt(i + 1) == c) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append("''");
                continue;
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
                continue;
            }
            if (c == '#') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private int resolveLimit(AnalysisQueryDTO queryDTO, Integer rowLimit) {
        if (rowLimit != null && rowLimit > 0) {
            return Math.min(rowLimit, MAX_LIMIT);
        }
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

    /**
     * 保存规则用的严格判据：字段名必须<b>本来就是</b>合法标识符，不能被清洗后通过。
     * <p>
     * 查询侧沿用上面的清洗（前端传来的列名带杂字符时尽量救回一个能用的名字），但规则是长期存在的配置：
     * {@code salary; drop} 被清洗成 {@code salarydrop} 会静默存成一条永远匹配不到的规则，
     * 症状是"规则配了没生效"，比当场拒绝难查得多。
     */
    public static String requireIdentifier(String raw, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "不是合法列名: " + raw);
        }
        return value;
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

    /**
     * LIKE 里的 % 与 _ 是通配符，用户输入带它们时按字面匹配更符合直觉。
     * <p>
     * 空值必须拒绝（API-116）：原先 {@code String.valueOf(null)} 会得到字面串 {@code "null"}，
     * 生成 {@code LIKE '%null%'} —— 一个"没填关键词"的过滤器于是悄悄变成"只匹配含 null 三个字母的行"，
     * 比空结果更难查。同 {@code eq} 的口径。
     */
    private String likeLiteral(Object value) {
        if (value == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "过滤值不能为空");
        }
        if (value instanceof Collection) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该操作符的取值不接受数组");
        }
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
