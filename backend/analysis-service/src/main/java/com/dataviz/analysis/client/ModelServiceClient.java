package com.dataviz.analysis.client;

import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * model-service 内部元数据接口客户端：查询引擎要知道 datasetId 背后的真实表名（或自定义 SQL）
 * 与绑定的数据源，这两件事都不该由 analysis 侧猜（原先是拼成 dataset_{id}，永远查不到表）。
 * <p>
 * "数据集不存在"与"服务调不通"必须分开（D41）：前者是本请求的确定结论（404 语义），后者要报 503，
 * 否则一次网络抖动会被写成"数据集不存在"，把配置问题伪装成数据问题。
 */
@Slf4j
@Component
public class ModelServiceClient {

    private static final String DATASET_META_PATH = "/api/model/internal/dataset/";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public ModelServiceClient(RestTemplateBuilder builder,
                              @Value("${model-service.base-url:http://localhost:8085}") String baseUrl,
                              @Value("${internal.api.token:}") String internalToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @return null 表示数据集确实不存在（含已逻辑删除）
     */
    public DatasetMeta getDatasetMeta(Long datasetId) {
        try {
            ResponseEntity<R<DatasetMeta>> response = restTemplate.exchange(
                    baseUrl + DATASET_META_PATH + datasetId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<R<DatasetMeta>>() { });
            R<DatasetMeta> payload = response.getBody();
            if (payload == null) {
                throw unavailable("数据集元数据返回空响应体");
            }
            if (!payload.isSuccess()) {
                throw new BizException(payload.getCode(), payload.getMessage());
            }
            return payload.getData();
        } catch (RestClientResponseException e) {
            log.error("读取数据集元数据失败: datasetId={}, status={}, body={}",
                    datasetId, e.getRawStatusCode(), e.getResponseBodyAsString());
            throw unavailable("模型服务返回异常状态 " + e.getRawStatusCode());
        } catch (RestClientException e) {
            throw unavailable("无法访问模型服务: " + e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        return headers;
    }

    private BizException unavailable(String message) {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, "数据集元数据读取失败：" + message);
    }
}
