package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 8.x over REST（9200，本地容器 security off；凭据按配置带上请求头）。
 * <p>
 * 协议口径（对 8.12.0 实测）：
 * <ul>
 *   <li>连通性：{@code GET /} → 集群 JSON，必须能读到 {@code version.number}，否则说明对端根本不是
 *       ES（端口撞车是这类"配置错连到别的服务"最常见的形态）；</li>
 *   <li>查询：{@code POST /_sql}，请求体 {@code {"query": "SELECT …"}} →
 *       {@code {"columns":[{"name":…,"type":…}],"rows":[[…]]}}；</li>
 *   <li>列表：{@code GET /_cat/indices?format=json}，过滤 {@code .} 前缀系统索引（.kibana、.ds-* 等），
 *       只回用户索引；</li>
 *   <li>凭据只进 Authorization 头（Basic 或 {@code ApiKey}），绝不进 URL/日志/异常文案。</li>
 * </ul>
 * <b>注意：ES 的 {@code _sql} 不是任意 SQL</b> —— 它只接受 ES SQL 语法子集（不支持 MySQL/PG 方言、
 * 跨源 JOIN 等）。所以一旦 ES 返回错误，必须把 <b>ES 自己的 error.reason 原文</b>带出去
 * （如 {@code Encountered "\"" at line 1, column 1.}），塌成"SQL执行失败"这种泛化文案会让用户
 * 完全无从知道该改哪段语法。HTTP 错误体与 200-带-error 两种形态都要解析。
 * <p>
 * 值映射：ES 的 JSON 已是带类型的（整数就是 JSON number），直接 treeToValue 还原成
 * Integer/Long/Double/String/Map/List，无需 ClickHouse 那套字符串→数字的按 type 转换。
 * maxRows 是客户端截断（_sql 默认单次 fetch_size，服务端不做行数上限），timeoutSeconds 落成
 * RestTemplate 读超时；ES 侧没有 per-request SQL 超时参数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchConnector implements DatasourceConnector {

    static final int DEFAULT_PORT = 9200;
    private static final int TEST_TIMEOUT_SECONDS = 5;
    private static final int LIST_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final ConnectorRestClientFactory restClients;

    @Override
    public boolean supports(DatasourceType type) {
        return type == DatasourceType.ELASTICSEARCH;
    }

    @Override
    public ConnectorCapability capability() {
        return ConnectorCapability.dataAccess();
    }

    @Override
    public boolean testConnection(String configJson) {
        ConnectorConfig config = parse(configJson);
        try {
            ResponseEntity<String> response = exchange(config, HttpMethod.GET,
                    config.baseUrl() + "/", null, TEST_TIMEOUT_SECONDS);
            JsonNode root = readJson(response.getBody(), "Elasticsearch 根路径响应不是合法 JSON（对端可能不是 ES）");
            String version = root.at("/version/number").asText(null);
            if (!StringUtils.hasText(version)) {
                throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                        "Elasticsearch 根路径响应缺少 version.number，对端可能不是 ES 服务: " + config.hostPort());
            }
            log.info("Elasticsearch 连接测试通过: {} version={}", config.hostPort(), version);
            return true;
        } catch (RestClientResponseException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "Elasticsearch 连接测试失败: " + config.hostPort() + " - " + vendorError(e));
        } catch (ResourceAccessException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "Elasticsearch HTTP 连接失败: " + config.hostPort() + " - " + e.getMessage());
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "Elasticsearch HTTP 连接失败: " + config.hostPort() + " - " + e.getMessage());
        }
    }

    @Override
    public List<String> listTables(String configJson) {
        ConnectorConfig config = parse(configJson);
        JsonNode root;
        try {
            ResponseEntity<String> response = exchange(config, HttpMethod.GET,
                    config.baseUrl() + "/_cat/indices?format=json", null, LIST_TIMEOUT_SECONDS);
            root = readJson(response.getBody(), "Elasticsearch _cat/indices 响应不是合法 JSON");
        } catch (RestClientResponseException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "Elasticsearch 获取索引列表失败: " + vendorError(e));
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "Elasticsearch 获取索引列表失败: " + e.getMessage());
        }
        List<String> indices = new ArrayList<>();
        for (JsonNode item : root) {
            String name = item.path("index").asText(null);
            // . 前缀是系统索引（.kibana、.ds-…、.security-…），不是用户数据
            if (StringUtils.hasText(name) && !name.startsWith(".")) {
                indices.add(name);
            }
        }
        Collections.sort(indices);
        return indices;
    }

    @Override
    public DatasourceQueryResult query(String configJson, String sql, int maxRows, int timeoutSeconds) {
        ConnectorConfig config = parse(configJson);
        long startTime = System.currentTimeMillis();
        JsonNode root;
        try {
            String body;
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("query", sql);
                body = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "序列化 _sql 请求体失败");
            }
            ResponseEntity<String> response = exchange(config, HttpMethod.POST,
                    config.baseUrl() + "/_sql", body, timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_QUERY_TIMEOUT_SECONDS);
            root = readJson(response.getBody(), "Elasticsearch _sql 响应不是合法 JSON");
        } catch (RestClientResponseException e) {
            // ES 的 SQL 解析/执行错误优先取它自己的 error.reason —— 泛化文案掩盖语法差异（见类注释）
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, vendorError(e));
        } catch (ResourceAccessException e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "查询超时: " + e.getMessage());
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "Elasticsearch 查询失败: " + e.getMessage());
        }
        // 少数 ES 错误以 200 + error 字段返回，同样必须原文上浮
        String embeddedError = extractErrorReason(root);
        if (embeddedError != null) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, embeddedError);
        }

        List<String> columns = new ArrayList<>();
        for (JsonNode column : root.path("columns")) {
            columns.add(column.path("name").asText());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        JsonNode dataRows = root.path("rows");
        for (int i = 0; i < dataRows.size(); i++) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            JsonNode dataRow = dataRows.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int col = 0; col < columns.size(); col++) {
                JsonNode cell = dataRow.isArray() && col < dataRow.size() ? dataRow.get(col) : null;
                row.put(columns.get(col), toPlainValue(cell));
            }
            rows.add(row);
        }

        DatasourceQueryResult result = new DatasourceQueryResult();
        result.setColumns(columns);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(truncated);
        result.setExecutionTime(System.currentTimeMillis() - startTime);
        result.setSql(sql);
        return result;
    }

    // ---- internals ---------------------------------------------------------

    private ConnectorConfig parse(String configJson) {
        return ConnectorConfig.parse(objectMapper, configJson, DEFAULT_PORT, "Elasticsearch");
    }

    private ResponseEntity<String> exchange(ConnectorConfig config, HttpMethod method, String url,
                                            String jsonBody, int readTimeoutSeconds) {
        HttpHeaders headers = new HttpHeaders();
        applyAuth(config, headers);
        RestTemplate restTemplate = restClients.create(readTimeoutSeconds);
        if (jsonBody == null) {
            return restTemplate.exchange(url, method, new HttpEntity<>(headers), String.class);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, method, new HttpEntity<>(jsonBody, headers), String.class);
    }

    /** 凭据只进头：username 非空用 Basic；否则 apiKey 用 ES 的 ApiKey 头；都没有（security off）就不带。 */
    private void applyAuth(ConnectorConfig config, HttpHeaders headers) {
        if (config.hasBasicAuth()) {
            headers.set(HttpHeaders.AUTHORIZATION, config.basicAuthHeader());
        } else if (config.hasApiKey()) {
            headers.set(HttpHeaders.AUTHORIZATION, "ApiKey " + config.getApiKey());
        }
    }

    private JsonNode readJson(String raw, String failureHint) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, failureHint);
        }
    }

    private Object toPlainValue(JsonNode cell) {
        if (cell == null || cell.isNull() || cell.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(cell, Object.class);
        } catch (Exception e) {
            return cell.asText();
        }
    }

    /** 把 ES 错误体里的自有文案提出来：error.reason（_sql 常用）→ error 本身是字符串 → error_message。 */
    private String vendorError(RestClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        try {
            JsonNode root = objectMapper.readTree(raw);
            String reason = extractErrorReason(root);
            if (reason != null) {
                return reason;
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体（网关页等）落到下面的原文截断
        }
        if (raw == null || raw.trim().isEmpty()) {
            return "Elasticsearch 返回 HTTP " + e.getRawStatusCode() + "，无错误详情";
        }
        String trimmed = raw.trim();
        return "Elasticsearch 返回 HTTP " + e.getRawStatusCode() + ": "
                + (trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed);
    }

    private String extractErrorReason(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            if (error.isTextual()) {
                return error.asText();
            }
            String reason = error.path("reason").asText(null);
            if (StringUtils.hasText(reason)) {
                return reason;
            }
        }
        JsonNode errorMessage = root.get("error_message");
        if (errorMessage != null && errorMessage.isTextual() && StringUtils.hasText(errorMessage.asText())) {
            return errorMessage.asText();
        }
        // 顶层字符串 "error": "parsing_exception" 之外的失败标记
        if (root.has("error") && root.path("status").isNumber() && root.path("status").asInt() >= 400) {
            return "Elasticsearch 返回错误状态 " + root.path("status").asInt();
        }
        return null;
    }
}
