package com.dataviz.etl.engine.operator.impl;

import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.sql.SqlIdentifiers;
import com.dataviz.etl.engine.DagRunSummary;
import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Source node: reads rows out of a datasource through datasource-service.
 * <p>
 * Config parameters:
 * - datasourceId: Long - required, which datasource to read from
 * - sql: String - optional, a hand-written SELECT; when present it wins over tableName
 * - tableName: String - required when sql is absent
 * - columns: List&lt;String&gt; | "a,b" - projection, default *
 * - whereClause: String - optional, appended verbatim
 * - limit: Integer - optional row cap
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInputOperator implements Operator {

    /**
     * 单次读取的行数天花板。服务端还会再夹一次，所以这里的值只影响"什么时候开始报截断"，
     * 不会让请求越过服务端的上限。
     */
    private static final int READ_ROW_CEILING = DagRunSummary.READ_ROW_CEILING;

    private final DatasourceQueryClient queryClient;

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();

        Long datasourceId = context.getConfigLong("datasourceId");
        if (datasourceId == null) {
            return NodeResult.failure("源端节点缺少 datasourceId，无法确定从哪个数据源取数");
        }

        String sql;
        try {
            sql = buildSql(context);
        } catch (RuntimeException e) {
            return NodeResult.failure(e.getMessage());
        }

        try {
            DatasourceQueryResult result = queryClient.query(datasourceId, sql, READ_ROW_CEILING, null);
            List<Map<String, Object>> rows = result.getRows() == null
                    ? new ArrayList<>() : new ArrayList<>(result.getRows());

            long duration = System.currentTimeMillis() - start;
            log.info("源端读取完成: datasourceId={}, rows={}, truncated={}, {}ms",
                    datasourceId, rows.size(), result.isTruncated(), duration);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sql", sql);
            metadata.put("truncated", result.isTruncated());
            return NodeResult.builder()
                    .success(true)
                    .outputData(rows)
                    .rowCount(rows.size())
                    .durationMs(duration)
                    .metadata(metadata)
                    .build();
        } catch (RuntimeException e) {
            log.error("源端读取失败: datasourceId={}, sql={}", datasourceId, sql, e);
            return NodeResult.failure("源端读取失败: " + e.getMessage());
        }
    }

    /**
     * 手写 SQL 优先 —— 设计器的「自定义 SQL」模式就靠它，取数面等于该数据源能 SELECT 到的东西；
     * 只读白名单与行数天花板由 datasource-service 把。表名模式下标识符走白名单，whereClause
     * 原样拼接（它和自定义 SQL 同一信任级，收紧它没有意义，除非整条 SQL 通道一起收）。
     */
    private String buildSql(NodeContext context) {
        String rawSql = context.getConfigString("sql");
        if (rawSql != null && !rawSql.trim().isEmpty()) {
            return rawSql.trim();
        }

        String table = SqlIdentifiers.require(context.getConfigString("tableName"), "源表");
        List<String> columns = SqlIdentifiers.requireAll(context.getConfigList("columns"), "源列");
        String select = columns.isEmpty() ? "*" : String.join(", ", columns);

        StringBuilder sql = new StringBuilder("SELECT ").append(select).append(" FROM ").append(table);
        String whereClause = context.getConfigString("whereClause");
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            sql.append(" WHERE ").append(whereClause.trim());
        }
        Integer limit = context.getConfigInt("limit");
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ").append(Math.min(limit, READ_ROW_CEILING));
        }
        return sql.toString();
    }

    @Override
    public String getType() {
        return "database_input";
    }
}
