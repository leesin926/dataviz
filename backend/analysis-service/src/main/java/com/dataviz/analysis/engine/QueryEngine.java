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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Query engine that processes visual analysis queries.
 * <p>
 * Pipeline: Resolve dataset -> Generate SQL -> Apply permission filters -> Execute -> Cache result
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryEngine {

    private final SqlBuilder sqlBuilder;
    private final PermissionFilter permissionFilter;
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

        // 2. Build SQL from visual config, then apply permission filters
        String sql = sqlBuilder.buildSql(queryDTO, meta);
        String filteredSql = permissionFilter.applyPermission(sql, userId, tenantId);
        log.debug("Final SQL: {}", filteredSql);

        // 3. Cache lookup happens after the SQL is final, so the key covers permissions as well
        QueryResultVO cached = readCache(filteredSql, tenantId, userId);
        if (cached != null) {
            cached.setExecutionTime(System.currentTimeMillis() - start);
            log.info("Query cache hit for dataset: {}", queryDTO.getDatasetId());
            return cached;
        }

        // 4. Execute against the bound datasource (read-only endpoint; this service holds no credentials)
        DatasourceQueryResult data = datasourceQueryClient.query(
                meta.getDatasourceId(), filteredSql, resolveRowLimit(queryDTO), null);

        QueryResultVO result = new QueryResultVO();
        result.setColumns(data.getColumns() == null ? Collections.emptyList() : data.getColumns());
        List<Map<String, Object>> rows = data.getRows() == null ? Collections.emptyList() : data.getRows();
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setSql(filteredSql);
        result.setExecutionTime(System.currentTimeMillis() - start);

        writeCache(filteredSql, tenantId, userId, result);

        log.info("Query completed: {} rows in {}ms", result.getRowCount(), result.getExecutionTime());
        return result;
    }

    private int resolveRowLimit(AnalysisQueryDTO queryDTO) {
        Integer limit = queryDTO.getLimit();
        if (limit == null || limit <= 0) {
            return SqlBuilder.DEFAULT_LIMIT;
        }
        return Math.min(limit, ROWS_CEILING);
    }

    private String cacheKey(String sql, Long tenantId, Long userId) {
        return CACHE_PREFIX + tenantId + ":" + userId + ":" + sha256Hex(sql);
    }

    private QueryResultVO readCache(String sql, Long tenantId, Long userId) {
        if (cacheTtlSeconds <= 0) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey(sql, tenantId, userId));
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

    private void writeCache(String sql, Long tenantId, Long userId, QueryResultVO result) {
        if (cacheTtlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(sql, tenantId, userId),
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
