package com.dataviz.alert.client;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * datasource-service 内部只读查询客户端。
 * <p>
 * 告警不自己持 JDBC 连接与数据源口令：取数统一走这里，连接池、只读白名单、超时与行数上限都由
 * datasource-service 一处兜住。<b>取不到值</b>（SQL 返回空集）与<b>取数失败</b>（服务不可用/口令不一致）
 * 是两件事，前者返回 null 让上层按"无法判定"处理，后者抛 503 —— 把库故障算成"未越界"会造成漏报，
 * 而漏报比误报更难发现（见 D41）。
 */
@Slf4j
@Component
public class DatasourceQueryClient {

    private static final String QUERY_PATH = "/api/datasource/internal/query";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public DatasourceQueryClient(@Qualifier("restTemplate") RestTemplate restTemplate,
                                 @Value("${datasource-service.base-url}") String baseUrl,
                                 @Value("${internal.api.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 执行返回单值的指标 SQL，取首行首列。
     *
     * @return null 表示查询成功但没有结果行（无法判定，不等于越界）
     */
    @SuppressWarnings("unchecked")
    public BigDecimal queryScalar(Long datasourceId, String sql) {
        Map<String, Object> result = query(datasourceId, sql);
        Object rowsValue = result.get("rows");
        if (!(rowsValue instanceof List) || ((List<Object>) rowsValue).isEmpty()) {
            log.warn("指标查询无结果行: datasourceId={}, sql={}", datasourceId, sql);
            return null;
        }
        Object firstRow = ((List<Object>) rowsValue).get(0);
        if (!(firstRow instanceof Map) || ((Map<String, Object>) firstRow).isEmpty()) {
            log.warn("指标查询返回空列: datasourceId={}, sql={}", datasourceId, sql);
            return null;
        }
        Object value = ((Map<String, Object>) firstRow).values().iterator().next();
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
                    "指标 SQL 的首列不是数值，无法与阈值比较: " + value.getClass().getSimpleName());
        }
    }

    /** @return datasource-service 的原始结果（columns/rows/rowCount/executionTime/sql） */
    public Map<String, Object> query(Long datasourceId, String sql) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasourceId", datasourceId);
        body.put("sql", sql);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        try {
            ResponseEntity<R<Map<String, Object>>> response = restTemplate.exchange(
                    baseUrl + QUERY_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<R<Map<String, Object>>>() { });
            R<Map<String, Object>> payload = response.getBody();
            if (payload == null) {
                throw unavailable("数据源查询返回空响应体");
            }
            if (!payload.isSuccess()) {
                // 数据源不存在 / SQL 语法错 / 非只读 —— 保留原始 code，让上层日志说清真原因
                throw new BizException(payload.getCode(), payload.getMessage());
            }
            Map<String, Object> data = payload.getData();
            if (data == null) {
                throw unavailable("数据源查询无返回数据");
            }
            return data;
        } catch (RestClientResponseException e) {
            if (e.getRawStatusCode() == HttpStatus.FORBIDDEN.value()) {
                log.error("内部口令被数据源服务拒绝: {} —— 请核对两边 internal.api.token 是否一致", QUERY_PATH);
                throw unavailable("内部口令不被数据源服务接受");
            }
            log.error("数据源查询失败: status={}, body={}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw unavailable("数据源服务返回异常状态 " + e.getRawStatusCode());
        } catch (RestClientException e) {
            throw unavailable("无法访问数据源服务: " + e.getMessage());
        }
    }

    private BizException unavailable(String message) {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, "指标取数失败：" + message);
    }
}
