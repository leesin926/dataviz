package com.dataviz.common.core.client;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * datasource-service 内部只读查询端点的共享客户端。
 * <p>
 * 业务服务不各自持 JDBC 连接与数据源口令：取数统一走这里，连接池、只读白名单、超时与行数上限由
 * datasource-service 一处兜住。<b>取不到值</b>（SQL 返回空集）与<b>取数失败</b>（服务不可用/口令不一致）
 * 是两件事，前者返回空结果让上层按"无法判定"处理，后者抛 503 —— 把库故障算成"没数据"会造成漏报，
 * 而漏报比误报更难发现（见 D41）。
 * <p>
 * RestTemplate 由本类自建而不是注入：各服务已有的 restTemplate bean 超时口径不同（告警通知就要短超时），
 * 共用一个 bean 会让"谁注入了哪个"变成隐式契约；自建只依赖配置项，代价是多一个连接管理器。
 */
@Slf4j
@Component
public class DatasourceQueryClient {

    private static final String QUERY_PATH = "/api/datasource/internal/query";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public DatasourceQueryClient(RestTemplateBuilder builder,
                                 ObjectMapper objectMapper,
                                 @Value("${datasource-service.base-url:http://localhost:8083}") String baseUrl,
                                 @Value("${internal.api.token:}") String internalToken,
                                 @Value("${datasource-service.query-read-timeout-seconds:35}") int readTimeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.internalToken = internalToken;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds > 0 ? readTimeoutSeconds : 35))
                .build();
    }

    /**
     * 执行只读 SQL 并返回结果集。
     *
     * @param maxRows         期望最大行数，服务端还会再夹一次上限
     * @param timeoutSeconds  单条 SQL 超时（秒），服务端同样有天花板
     */
    public DatasourceQueryResult query(Long datasourceId, String sql, Integer maxRows, Integer timeoutSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasourceId", datasourceId);
        body.put("sql", sql);
        if (maxRows != null && maxRows > 0) {
            body.put("maxRows", maxRows);
        }
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            body.put("timeout", timeoutSeconds);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        try {
            ResponseEntity<R<DatasourceQueryResult>> response = restTemplate.exchange(
                    baseUrl + QUERY_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<R<DatasourceQueryResult>>() { });
            R<DatasourceQueryResult> payload = response.getBody();
            if (payload == null) {
                throw unavailable("数据源查询返回空响应体");
            }
            if (!payload.isSuccess()) {
                throw new BizException(payload.getCode(), payload.getMessage());
            }
            DatasourceQueryResult data = payload.getData();
            if (data == null) {
                throw unavailable("数据源查询无返回数据");
            }
            return data;
        } catch (RestClientResponseException e) {
            throw translate(e, sql);
        } catch (RestClientException e) {
            throw unavailable("无法访问数据源服务: " + e.getMessage());
        }
    }

    public DatasourceQueryResult query(Long datasourceId, String sql) {
        return query(datasourceId, sql, null, null);
    }

    /**
     * 执行返回单值的 SQL（首行首列）。
     *
     * @return null 表示查询成功但没有结果行 —— 这是"无法判定"，不是"值为 0"
     */
    public BigDecimal queryScalar(Long datasourceId, String sql) {
        DatasourceQueryResult result = query(datasourceId, sql);
        List<Map<String, Object>> rows = result.getRows();
        if (rows.isEmpty()) {
            log.warn("查询无结果行: datasourceId={}, sql={}", datasourceId, sql);
            return null;
        }
        Map<String, Object> firstRow = rows.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            log.warn("查询返回空列: datasourceId={}, sql={}", datasourceId, sql);
            return null;
        }
        Object value = firstRow.values().iterator().next();
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "SQL 的首列不是数值，无法比较: " + value.getClass().getSimpleName());
        }
    }

    /**
     * 把数据源服务的错误响应还原成原始错误码：表不存在、语法错这类原因必须能传到调用方日志里，
     * 统一塌成 503 会把配置错误伪装成"服务挂了"。
     */
    private BizException translate(RestClientResponseException e, String sql) {
        String rawBody = e.getResponseBodyAsString();
        if (e.getRawStatusCode() == HttpStatus.FORBIDDEN.value()) {
            log.error("内部口令被数据源服务拒绝: {} —— 请核对两边 internal.api.token 是否一致", QUERY_PATH);
            return unavailable("内部口令不被数据源服务接受");
        }
        R<Void> payload = tryParse(rawBody);
        if (payload != null && !payload.isSuccess() && payload.getMessage() != null) {
            log.error("数据源查询失败: status={}, code={}, message={}, sql={}",
                    e.getRawStatusCode(), payload.getCode(), payload.getMessage(), sql);
            return new BizException(payload.getCode(), payload.getMessage());
        }
        log.error("数据源查询失败: status={}, body={}, sql={}", e.getRawStatusCode(), rawBody, sql);
        return unavailable("数据源服务返回异常状态 " + e.getRawStatusCode());
    }

    private R<Void> tryParse(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, new TypeReference<R<Void>>() { });
        } catch (Exception ignored) {
            return null;
        }
    }

    private BizException unavailable(String message) {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, "数据源查询失败：" + message);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
