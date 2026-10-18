package com.dataviz.etl.engine.operator.impl;

import com.dataviz.common.core.client.DatasourceWriteClient;
import com.dataviz.common.core.sql.SqlIdentifiers;
import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Sink node: writes the upstream rows into a table through datasource-service.
 * <p>
 * Config parameters:
 * - datasourceId: Long - required
 * - tableName: String - required
 * - writeMode / mode: String - append (only one implemented); overwrite/upsert are rejected
 * - columns: List&lt;String&gt; | "a,b" - target columns, default = 上游首行的列顺序
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseOutputOperator implements Operator {

    private static final String MODE_APPEND = "append";

    private final DatasourceWriteClient writeClient;

    @Override
    public NodeResult execute(NodeContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();

        Long datasourceId = context.getConfigLong("datasourceId");
        if (datasourceId == null) {
            return NodeResult.failure("目标节点缺少 datasourceId，无法确定写入哪个数据源");
        }
        String mode = readMode(context);
        if (!MODE_APPEND.equals(mode)) {
            // overwrite 要先 TRUNCATE/DELETE，upsert 要先定主键 —— 两者都是不可回滚的动作，
            // 不能在这里"顺手"实现成 append：那样症状是目标表一直长，而设计器上明明选了覆盖写。
            return NodeResult.failure("目标写入方式暂不支持: " + mode
                    + "（当前只实现 append，请改设计器里的写入方式为追加，或先手工清空目标表）");
        }

        String table;
        List<String> columns;
        try {
            table = SqlIdentifiers.require(context.getConfigString("tableName"), "目标表");
            columns = resolveColumns(context, inputData);
        } catch (RuntimeException e) {
            return NodeResult.failure(e.getMessage());
        }

        if (inputData == null || inputData.isEmpty()) {
            long emptyDuration = System.currentTimeMillis() - start;
            log.info("目标端跳过写入: 表 {} 没有上游数据行", table);
            return NodeResult.success(new ArrayList<>(), 0, emptyDuration);
        }

        List<List<Object>> rows = new ArrayList<>(inputData.size());
        for (Map<String, Object> source : inputData) {
            List<Object> row = new ArrayList<>(columns.size());
            for (String column : columns) {
                row.add(source == null ? null : source.get(column));
            }
            rows.add(row);
        }

        try {
            int written = writeClient.write(datasourceId, table, columns, rows);
            long duration = System.currentTimeMillis() - start;
            log.info("目标端写入完成: datasourceId={}, table={}, rows={}, {}ms",
                    datasourceId, table, written, duration);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("targetTable", table);
            metadata.put("rowsWritten", written);
            return NodeResult.builder()
                    .success(true)
                    .outputData(inputData)
                    .rowCount(written)
                    .durationMs(duration)
                    .metadata(metadata)
                    .build();
        } catch (RuntimeException e) {
            log.error("目标端写入失败: datasourceId={}, table={}", datasourceId, table, e);
            return NodeResult.failure("目标端写入失败: " + e.getMessage());
        }
    }

    /**
     * 写入列缺省时取上游首行的键序。
     * <p>
     * 只认首行是有代价的：后面的行若有额外列会被丢掉，缺的列写 null。这个代价比"每行各自取键、
     * 结果一批里出现两种列形状"要小 —— 后者到服务端只能整批拒掉，症状是一行都写不进去。
     * 上游若是聚合/投影节点，列集合本来就是固定的。
     */
    private List<String> resolveColumns(NodeContext context, List<Map<String, Object>> inputData) {
        List<String> configured = context.getConfigList("columns");
        if (!configured.isEmpty()) {
            return SqlIdentifiers.requireAll(configured, "目标列");
        }
        if (inputData == null || inputData.isEmpty() || inputData.get(0) == null) {
            return Collections.emptyList();
        }
        return SqlIdentifiers.requireAll(new ArrayList<>(inputData.get(0).keySet()), "目标列");
    }

    /** 设计器存 writeMode，演示数据存 mode，两边都接（键名不同而已，语义一致）。 */
    private String readMode(NodeContext context) {
        String mode = context.getConfigString("writeMode");
        if (mode == null || mode.trim().isEmpty()) {
            mode = context.getConfigString("mode");
        }
        return mode == null || mode.trim().isEmpty() ? MODE_APPEND : mode.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getType() {
        return "database_output";
    }
}
