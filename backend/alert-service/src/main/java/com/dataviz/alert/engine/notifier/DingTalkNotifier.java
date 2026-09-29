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
 * 钉钉群机器人通知。
 * <p>
 * 必须读响应体里的 errcode：机器人 token 无效时钉钉返回的是 <b>HTTP 200 + errcode!=0</b>，
 * 只看状态码会把"一条都没发出去"记成"通知成功"。
 */
@Slf4j
@Component
public class DingTalkNotifier implements AlertNotifier {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DingTalkNotifier(@Qualifier("notifyRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "DINGTALK";
    }

    @Override
    public List<ChannelField> fields() {
        return ChannelField.list(
                // 地址本身就带 access_token，所以既是 URL 又是口令：两个标记都要，缺一个就会漏（要么明文回给前端，要么保存侧不判地址段）
                ChannelField.of("webhook", ChannelField.KIND_TEXT).required().secret().url()
                        .label("channel.field.dingtalkWebhook")
                        .placeholder("https://oapi.dingtalk.com/robot/send?access_token=..."),
                // @ 谁（原先的 mobiles）改由规则挂的通知组提供，见 NotifyTargets；
                // atAll 留在这里：它是"这个群机器人要不要@所有人"的行为开关，不是"发给谁"
                ChannelField.of("atAll", ChannelField.KIND_SWITCH).label("channel.field.atAll")
        );
    }

    @Override
    public String targetKind() {
        return TARGET_MOBILE;
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        // 群机器人地址里带 access_token，日志和告警记录只留前缀，不把口令落库
        String webhook = webhookUrlOf(channelConfig);
        int tokenAt = webhook.indexOf("access_token=");
        String masked = tokenAt < 0 ? webhook : webhook.substring(0, tokenAt) + "access_token=***";
        return targets.mobiles().isEmpty() ? masked : masked + " @ " + String.join(",", targets.mobiles());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        String webhook = OutboundUrlGuard.requireAllowed(
                NotifierSupport.requireText(config, "webhook", channelConfig.getName()),
                channelConfig.getName());

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", NotifierSupport.formatMessage(rule, event));

        Map<String, Object> at = new LinkedHashMap<>();
        List<String> mobiles = targets.mobiles();
        at.put("atMobiles", mobiles);
        at.put("isAtAll", Boolean.TRUE.equals(config.get("atAll")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", text);
        body.put("at", at);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "钉钉机器人返回非 2xx: " + response.getStatusCode());
        }
        Integer errcode = readErrcode(response.getBody());
        if (errcode == null || errcode != 0) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "钉钉机器人拒绝消息: errcode=" + errcode + ", body=" + safeBody(response.getBody()));
        }
        log.info("钉钉通知已发送: eventId={}, ruleId={}, atAll={}", event.getId(), rule.getId(), at.get("isAtAll"));
    }

    private String webhookUrlOf(NotifyChannel channelConfig) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        return OutboundUrlGuard.requireAllowed(
                NotifierSupport.requireText(config, "webhook", channelConfig.getName()),
                channelConfig.getName());
    }

    private Integer readErrcode(String body) {
        if (body == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object value = parsed.get("errcode");
            return value instanceof Number ? ((Number) value).intValue() : null;
        } catch (Exception e) {
            // 响应不是 JSON（比如网关返回了一段 HTML），当成失败处理比当成成功安全
            return null;
        }
    }

    private String safeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }
}
