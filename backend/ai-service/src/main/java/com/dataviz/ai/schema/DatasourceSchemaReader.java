package com.dataviz.ai.schema;

import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从数据源读表结构，给 NL2SQL 当 prompt 的一部分。
 *
 * <p>不让大模型猜表名：没有 schema 的 NL2SQL 只能生成 {@code SELECT * FROM table_name}
 * 这种一眼假的语句，用户拿去执行必然报错，还不如一开始就说清楚"这次没拿到表结构"。
 * 取数统一走 {@link DatasourceQueryClient}（连接池、只读白名单、超时都在 datasource-service 一处兜住），
 * ai-service 自己不持有任何业务库口令。</p>
 *
 * <p>读不到不抛异常：schema 是"锦上添花"，缺了它 NL2SQL 仍然能给出结论，只是置信度更低。
 * 所以这里把失败原因带回去让调用方写进 {@code llmNote}，而不是把整条链路打断。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceSchemaReader {

    /** 上限：库里有几百张表时不能把整份 schema 塞进 prompt，token 会爆 */
    private static final int MAX_COLUMNS = 400;

    private static final String SCHEMA_SQL =
            "SELECT table_name, column_name, data_type, column_comment "
                    + "FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "ORDER BY table_name, ordinal_position "
                    + "LIMIT " + MAX_COLUMNS;

    private final DatasourceQueryClient queryClient;

    public SchemaResult read(Long datasourceId) {
        if (datasourceId == null) {
            return SchemaResult.empty("未指定数据源，本次生成不带表结构");
        }
        try {
            DatasourceQueryResult result = queryClient.query(datasourceId, SCHEMA_SQL, MAX_COLUMNS, 15);
            List<Map<String, Object>> rows = result.getRows();
            if (rows == null || rows.isEmpty()) {
                return SchemaResult.empty("数据源 " + datasourceId + " 里没读到表结构");
            }
            return SchemaResult.of(render(rows), rows.size());
        } catch (RuntimeException e) {
            // 数据源不可用/口令不一致/只读白名单拦下 information_schema 都落到这里
            log.warn("读取表结构失败: datasourceId={}, err={}", datasourceId, e.getMessage());
            return SchemaResult.empty("表结构读取失败：" + e.getMessage());
        }
    }

    private String render(List<Map<String, Object>> rows) {
        Map<String, StringBuilder> tables = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String table = str(row, "table_name");
            if (table.isEmpty()) {
                continue;
            }
            StringBuilder sb = tables.get(table);
            if (sb == null) {
                sb = new StringBuilder();
                tables.put(table, sb);
            }
            sb.append("  - ").append(str(row, "column_name"))
                    .append(' ').append(str(row, "data_type"));
            String comment = str(row, "column_comment");
            if (!comment.isEmpty()) {
                sb.append(" -- ").append(comment);
            }
            sb.append('\n');
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, StringBuilder> entry : tables.entrySet()) {
            out.append("表名: ").append(entry.getKey()).append('\n').append("列信息:\n").append(entry.getValue());
        }
        return out.toString();
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = null;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            // 不同 MySQL 版本/驱动下 information_schema 的列名大小写不一致，按忽略大小写取
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                value = entry.getValue();
                break;
            }
        }
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 读取结果：{@code schema} 为空表示没拿到，原因写在 {@code note} 里 */
    public static final class SchemaResult {
        private final String schema;
        private final String note;
        private final int columnCount;

        private SchemaResult(String schema, String note, int columnCount) {
            this.schema = schema;
            this.note = note;
            this.columnCount = columnCount;
        }

        static SchemaResult of(String schema, int columnCount) {
            return new SchemaResult(schema, null, columnCount);
        }

        static SchemaResult empty(String note) {
            return new SchemaResult("", note, 0);
        }

        public boolean isPresent() {
            return schema != null && !schema.isEmpty();
        }

        public String getSchema() {
            return schema;
        }

        public String getNote() {
            return note;
        }

        public int getColumnCount() {
            return columnCount;
        }
    }
}
