package com.dataviz.analysis.engine;

import com.dataviz.analysis.client.ModelServiceClient;
import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query engine that processes visual analysis queries.
 * <p>
 * Pipeline: Resolve dataset -> Resolve data permission -> Generate SQL -> Execute -> Cache result
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryEngine {

    private final SqlBuilder sqlBuilder;
    private final DataPermissionResolver dataPermissionResolver;
    private final StringRedisTemplate redisTemplate;
    private final ModelServiceClient modelServiceClient;
    private final DatasourceQueryClient datasourceQueryClient;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "query:cache:";

    /** 行数上限与 datasource-service 侧的天花板一致，传更大的值只会被它夹回去 */
    private static final int ROWS_CEILING = 5000;

    @Value("${analysis.query-cache.ttl-seconds:60}")
    private long cacheTtlSeconds;

    /**
     * Execute an analysis query through the full pipeline.
     */
    public QueryResultVO execute(AnalysisQueryDTO queryDTO, Long tenantId, Long userId) {
        return execute(queryDTO, tenantId, userId, null, null);
    }

    /**
     * @param rowLimit  本页行数；null 表示沿用查询自带的 limit（交互查询走这条）
     * @param rowOffset 偏移行数；导出全量时逐页往前推
     */
    public QueryResultVO execute(AnalysisQueryDTO queryDTO, Long tenantId, Long userId,
                                 Integer rowLimit, Integer rowOffset) {
        long start = System.currentTimeMillis();
        log.info("Executing query for dataset: {}, user: {}", queryDTO.getDatasetId(), userId);

        // 1. Resolve the dataset: real table/custom SQL and the datasource it belongs to
        DatasetMeta meta = modelServiceClient.getDatasetMeta(queryDTO.getDatasetId());
        if (meta == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在: " + queryDTO.getDatasetId());
        }
        if (meta.getDatasourceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据集未绑定数据源，无法执行查询: " + meta.getName());
        }
        if (tenantId != null && meta.getTenantId() != null && !String.valueOf(tenantId).equals(meta.getTenantId())) {
            // 查询列名与表名都来自数据集定义，跨租户引用别人的数据集定义本身就是越权，不能只靠前端不展示
            log.warn("拒绝跨租户查询: datasetId={}, datasetTenant={}, requestTenant={}",
                    queryDTO.getDatasetId(), meta.getTenantId(), tenantId);
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该数据集");
        }

        // 2. 行列数据权限：行谓词进 SQL 的 WHERE，受限列既挡显式引用也挡 SELECT * 的回列
        DataPermission permission = dataPermissionResolver.resolve(tenantId, userId, queryDTO.getDatasetId());
        rejectRestrictedReferences(queryDTO, permission);
        String sql = sqlBuilder.buildSql(queryDTO, meta, rowLimit, rowOffset, permission.rowPredicate());
        log.debug("Final SQL: {}", sql);

        // 3. Cache lookup happens after the SQL is final, and the key also carries the column
        //    restriction — 列权限不改 SQL 文本，只把它挡在键外会让新配的规则在 TTL 内继续漏列
        QueryResultVO cached = readCache(sql, permission, tenantId, userId);
        if (cached != null) {
            cached.setExecutionTime(System.currentTimeMillis() - start);
            log.info("Query cache hit for dataset: {}", queryDTO.getDatasetId());
            return maskRestrictedColumns(cached, permission);
        }

        // 4. Execute against the bound datasource (read-only endpoint; this service holds no credentials)
        DatasourceQueryResult data = datasourceQueryClient.query(
                meta.getDatasourceId(), sql, resolveRowLimit(queryDTO, rowLimit), null);

        QueryResultVO result = new QueryResultVO();
        result.setColumns(data.getColumns() == null ? Collections.<String>emptyList() : data.getColumns());
        List<Map<String, Object>> rows = data.getRows() == null ? Collections.<Map<String, Object>>emptyList() : data.getRows();
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(data.isTruncated());
        result.setSql(sql);
        result.setExecutionTime(System.currentTimeMillis() - start);

        QueryResultVO visible = maskRestrictedColumns(result, permission);
        writeCache(sql, permission, tenantId, userId, visible);

        log.info("Query completed: {} rows in {}ms", visible.getRowCount(), visible.getExecutionTime());
        return visible;
    }

    /**
     * 显式点名了受限列就直接拒绝，而不是悄悄少给一列。
     * <p>静默丢列的症状是"这个维度怎么总是空的"，用户不会知道是被权限遮了；报错至少能说出是哪一列。</p>
     */
    private void rejectRestrictedReferences(AnalysisQueryDTO queryDTO, DataPermission permission) {
        if (!permission.hasColumnRestriction()) {
            return;
        }
        Set<String> used = new LinkedHashSet<>();
        if (queryDTO.getDimensions() != null) {
            for (AnalysisQueryDTO.DimensionConfig item : queryDTO.getDimensions()) {
                addReference(used, item == null ? null : item.getField());
            }
        }
        if (queryDTO.getMetrics() != null) {
            for (AnalysisQueryDTO.MetricConfig item : queryDTO.getMetrics()) {
                addReference(used, item == null ? null : item.getField());
            }
        }
        if (queryDTO.getFilters() != null) {
            for (AnalysisQueryDTO.FilterConfig item : queryDTO.getFilters()) {
                addReference(used, item == null ? null : item.getField());
            }
        }
        if (queryDTO.getMetricFilters() != null) {
            for (AnalysisQueryDTO.MetricFilterConfig item : queryDTO.getMetricFilters()) {
                addReference(used, item == null ? null : item.getField());
            }
        }
        if (queryDTO.getOrders() != null) {
            for (AnalysisQueryDTO.OrderConfig item : queryDTO.getOrders()) {
                addReference(used, item == null ? null : item.getField());
            }
        }
        List<String> violated = new ArrayList<>();
        for (String column : permission.restrictedColumns()) {
            if (used.contains(normalize(column))) {
                violated.add(column);
            }
        }
        if (!violated.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "查询引用了无权访问的字段: " + String.join(", ", violated));
        }
    }

    private static void addReference(Set<String> sink, String field) {
        String normalized = normalize(field);
        if (!normalized.isEmpty()) {
            sink.add(normalized);
        }
    }

    /** 与 SqlBuilder.column 的清洗口径一致，避免 `部门 编码` 这类脏名字绕过比对 */
    private static String normalize(String field) {
        return field == null ? "" : field.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
    }

    /** SELECT * 时结果列来自数据源，受限列在这里裁掉；聚合查询的别名同名列也一并裁（聚合值本身就是泄露面） */
    private QueryResultVO maskRestrictedColumns(QueryResultVO result, DataPermission permission) {
        if (!permission.hasColumnRestriction() || result.getColumns() == null || result.getColumns().isEmpty()) {
            return result;
        }
        Set<String> hidden = new LinkedHashSet<>();
        for (String column : permission.restrictedColumns()) {
            hidden.add(normalize(column));
        }
        List<String> kept = new ArrayList<>(result.getColumns().size());
        for (String column : result.getColumns()) {
            if (column == null || !hidden.contains(normalize(column))) {
                kept.add(column);
            }
        }
        if (kept.size() == result.getColumns().size()) {
            return result;
        }
        result.setColumns(kept);
        if (result.getRows() != null) {
            for (Map<String, Object> row : result.getRows()) {
                if (row != null) {
                    row.keySet().removeIf(key -> key != null && hidden.contains(normalize(key)));
                }
            }
            result.setRowCount(result.getRows().size());
        }
        return result;
    }

    private int resolveRowLimit(AnalysisQueryDTO queryDTO, Integer rowLimit) {
        if (rowLimit != null && rowLimit > 0) {
            return Math.min(rowLimit, ROWS_CEILING);
        }
        Integer limit = queryDTO.getLimit();
        if (limit == null || limit <= 0) {
            return SqlBuilder.DEFAULT_LIMIT;
        }
        return Math.min(limit, ROWS_CEILING);
    }

    private String cacheKey(String sql, DataPermission permission, Long tenantId, Long userId) {
        return CACHE_PREFIX + tenantId + ":" + userId + ":" + sha256Hex(sql + "\n" + permission.fingerprint());
    }

    private QueryResultVO readCache(String sql, DataPermission permission, Long tenantId, Long userId) {
        if (cacheTtlSeconds <= 0) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey(sql, permission, tenantId, userId));
            if (payload == null || payload.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(payload, QueryResultVO.class);
        } catch (Exception e) {
            // 缓存只是加速，读失败绝不能把一次本来能成功的查询变成 500
            log.warn("查询缓存读取失败，回退到直接执行: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String sql, DataPermission permission, Long tenantId, Long userId, QueryResultVO result) {
        if (cacheTtlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(sql, permission, tenantId, userId),
                    objectMapper.writeValueAsString(result), Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("查询缓存写入失败: {}", e.getMessage());
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }
}
