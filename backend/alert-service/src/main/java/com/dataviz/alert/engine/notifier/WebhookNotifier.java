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
import java.util.List;
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
    public List<ChannelField> fields() {
        return ChannelField.list(
                ChannelField.of("url", ChannelField.KIND_TEXT)
                        .required().url()
                        .placeholder("http://192.168.1.10:18099/hook")
                        .label("channel.field.url"),
                // 接收方常常要求带鉴权头或对内容做签名校验；只支持固定头的话，能接的系统就只剩不设防的那几个
                ChannelField.of("headers", ChannelField.KIND_JSON)
                        .label("channel.field.headers")
                        .placeholder("{\"X-Token\":\"...\"}")
        );
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        return urlOf(channelConfig);
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        String url = urlOf(channelConfig);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Object declared = NotifierSupport.parseConfig(objectMapper, channelConfig).get("headers");
        if (declared instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) declared).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    headers.set(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }

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
        // 协议 + 地址段两件事一起判，判据在 OutboundUrlGuard 里（含"为什么不在保存侧校验"）
        return OutboundUrlGuard.requireAllowed(url, channelConfig.getName());
    }
}
