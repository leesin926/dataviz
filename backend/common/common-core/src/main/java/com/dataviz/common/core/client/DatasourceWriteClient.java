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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * datasource-service 内部批量写入端点的共享客户端（ETL 目标端落库走这里）。
 * <p>
 * 与 {@link DatasourceQueryClient} 同一判据：业务服务不各自持 JDBC 连接与数据源口令。区别在于
 * 这条通道<b>会改数据</b>，所以入参刻意是「表名 + 列名 + 标量值」的结构，服务端只拼 INSERT，
 * 调用方传不进任何 SQL 文本。
 * <p>
 * 单次调用的行数上限由服务端把住（超出直接拒），本客户端按上限自动分批：<b>不</b>截断。
 * 写端截断等于悄悄丢数据，而调用方看到的记账还是"成功"—— 这正是本仓库刚停掉的那种伪成功（见 D66④）。
 * 代价是一次任务的多批之间不是同一事务：某一批失败，前面几批已落库。这一点必须让上层日志能说清，
 * 所以返回值是「已写入行数」，不是「是否成功」。
 */
@Slf4j
@Component
public class DatasourceWriteClient {

    private static final String WRITE_PATH = "/api/datasource/internal/write";

    /** 与服务端 MAX_BATCH_ROWS 一致；改一边不改另一边，症状是"莫名 400" */
    private static final int BATCH_ROW_CEILING = 2000;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public DatasourceWriteClient(RestTemplateBuilder builder,
                                 ObjectMapper objectMapper,
                                 @Value("${datasource-service.base-url:http://localhost:8083}") String baseUrl,
                                 @Value("${internal.api.token:}") String internalToken,
                                 @Value("${datasource-service.write-read-timeout-seconds:60}") int readTimeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.internalToken = internalToken;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds > 0 ? readTimeoutSeconds : 60))
                .build();
    }

    /**
     * 批量写入一张表。
     *
     * @param rows 每行取值顺序必须与 columns 一致
     * @return 实际写入行数；0 表示本来就没有数据行（不是失败）
     */
    public int write(Long datasourceId, String table, List<String> columns, List<List<Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (columns == null || columns.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "批量写入缺少列名: " + table);
        }
        int written = 0;
        for (int from = 0; from < rows.size(); from += BATCH_ROW_CEILING) {
            int to = Math.min(from + BATCH_ROW_CEILING, rows.size());
            written += postBatch(datasourceId, table, columns, rows.subList(from, to));
        }
        log.info("批量写入完成: datasourceId={}, table={}, 声明 {} 行，写入 {} 行",
                datasourceId, table, rows.size(), written);
        return written;
    }

    private int postBatch(Long datasourceId, String table, List<String> columns, List<List<Object>> chunk) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasourceId", datasourceId);
        body.put("table", table);
        body.put("columns", columns);
        body.put("rows", chunk);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        try {
            ResponseEntity<R<Integer>> response = restTemplate.exchange(
                    baseUrl + WRITE_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<R<Integer>>() { });
            R<Integer> payload = response.getBody();
            if (payload == null) {
                throw unavailable("数据源写入返回空响应体");
            }
            if (!payload.isSuccess()) {
                throw new BizException(payload.getCode(), payload.getMessage());
            }
            Integer data = payload.getData();
            return data == null ? 0 : data;
        } catch (RestClientResponseException e) {
            throw translate(e, table, chunk.size());
        } catch (RestClientException e) {
            throw unavailable("无法访问数据源服务: " + e.getMessage());
        }
    }

    /** 与读侧同口径：写失败的原因（约束冲突、列不存在）必须能传到调用方日志，塌成 503 会把配置错误伪装成服务挂了。 */
    private BizException translate(RestClientResponseException e, String table, int rowCount) {
        String rawBody = e.getResponseBodyAsString();
        if (e.getRawStatusCode() == HttpStatus.FORBIDDEN.value()) {
            log.error("内部口令被数据源服务拒绝: {} —— 请核对两边 internal.api.token 是否一致", WRITE_PATH);
            return unavailable("内部口令不被数据源服务接受");
        }
        R<Void> payload = tryParse(rawBody);
        if (payload != null && !payload.isSuccess() && payload.getMessage() != null) {
            log.error("批量写入失败: status={}, code={}, message={}, table={}, rows={}",
                    e.getRawStatusCode(), payload.getCode(), payload.getMessage(), table, rowCount);
            return new BizException(payload.getCode(), payload.getMessage());
        }
        log.error("批量写入失败: status={}, body={}, table={}, rows={}",
                e.getRawStatusCode(), rawBody, table, rowCount);
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
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, "数据源写入失败：" + message);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
