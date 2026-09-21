package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 Webhook：把告警事件以 JSON POST 出去，非 2xx 视为发送失败。
 */
@Slf4j
@Component
public class WebhookNotifier implements AlertNotifier {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WebhookNotifier(@Qualifier("notifyRestTemplate") RestTemplate restTemplate,
                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "WEBHOOK";
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig) {
        return urlOf(channelConfig);
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event) {
        String url = urlOf(channelConfig);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId());
        payload.put("ruleId", rule.getId());
        payload.put("ruleName", rule.getName());
        payload.put("severity", event.getSeverity());
        payload.put("triggerValue", event.getTriggerValue());
        payload.put("condition", rule.getCondition());
        payload.put("threshold", rule.getThreshold());
        payload.put("metricExpression", rule.getMetricExpression());
        payload.put("datasourceId", rule.getDatasourceId());
        payload.put("message", NotifierSupport.formatMessage(rule, event));
        payload.put("createTime", event.getCreateTime() == null ? null : event.getCreateTime().toString());

        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Webhook 返回非 2xx: " + response.getStatusCode());
        }
        log.info("Webhook 通知已发送: url={}, eventId={}, status={}", url, event.getId(), response.getStatusCode());
    }

    private String urlOf(NotifyChannel channelConfig) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        String url = NotifierSupport.requireText(config, "url", channelConfig.getName());
        // 只放 http(s)：配置里写 file:// 或 jar:// 会变成让服务端去读本地盘
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook url 只支持 http/https");
        }
        return url;
    }
}
