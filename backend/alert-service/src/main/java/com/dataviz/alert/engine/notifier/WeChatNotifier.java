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
 * 企业微信群机器人通知（与"企业微信应用消息"是两回事，后者要先换 access_token，本类不做）。
 * <p>
 * 和钉钉同一个坑：<b>群机器人 token 无效时企业微信返回 HTTP 200 + errcode!=0</b>，
 * 只看状态码会把"一条都没送达"记成"通知成功"，所以响应体必须解析。
 */
@Slf4j
@Component
public class WeChatNotifier implements AlertNotifier {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeChatNotifier(@Qualifier("notifyRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "WECHAT";
    }

    @Override
    public List<ChannelField> fields() {
        return ChannelField.list(
                ChannelField.of("webhook", ChannelField.KIND_TEXT).required().secret().url()
                        .label("channel.field.wechatWebhook")
                        .placeholder("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=...")
                // @ 谁（原先的 mobiles）改由规则挂的通知组提供，见 NotifyTargets
        );
    }

    @Override
    public String targetKind() {
        return TARGET_MOBILE;
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        String webhook = webhookUrlOf(channelConfig);
        int keyAt = webhook.indexOf("key=");
        String masked = keyAt < 0 ? webhook : webhook.substring(0, keyAt) + "key=***";
        return targets.mobiles().isEmpty() ? masked : masked + " @ " + String.join(",", targets.mobiles());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        String webhook = NotifierSupport.requireText(config, "webhook", channelConfig.getName());
        webhook = OutboundUrlGuard.requireAllowed(webhook, channelConfig.getName());

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", NotifierSupport.formatMessage(rule, event));
        List<String> mobiles = targets.mobiles();
        if (!mobiles.isEmpty()) {
            // 空数组会让企业微信按"提及清单为空"处理，与其传一个没意义的字段不如不传
            text.put("mentioned_mobile_list", mobiles);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "企业微信机器人返回非 2xx: " + response.getStatusCode());
        }
        Integer errcode = readErrcode(response.getBody());
        if (errcode == null || errcode != 0) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "企业微信机器人拒绝消息: errcode=" + errcode + ", body=" + safeBody(response.getBody()));
        }
        log.info("企业微信通知已发送: eventId={}, ruleId={}, mentioned={}", event.getId(), rule.getId(), mobiles.size());
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
            // 响应不是 JSON（网关回了 HTML），当成失败比当成成功安全
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
