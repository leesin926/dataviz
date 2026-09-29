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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书自定义机器人（群 webhook）通知。
 * <p>
 * 和钉钉/企业微信同一类判据：<b>机器人被限流或签名不对时，飞书回的是 HTTP 200 + code!=0</b>，
 * 所以响应体里的状态码才是成败依据，HTTP 状态码只是"请求送到了"。
 * <p>
 * 加签是可选的（群里勾了"签名校验"才需要）：{@code secret} 留空就按不签名发，
 * 填了就走 HmacSHA256 —— 算法形状是飞书文档规定的那个反直觉写法：
 * <b>key = timestamp + "\n" + secret，被签名的数据是空字节数组</b>。
 */
@Slf4j
@Component
public class FeiShuNotifier implements AlertNotifier {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FeiShuNotifier(@Qualifier("notifyRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "FEISHU";
    }

    @Override
    public List<ChannelField> fields() {
        return ChannelField.list(
                ChannelField.of("webhook", ChannelField.KIND_TEXT).required().secret().url()
                        .label("channel.field.feishuWebhook")
                        .placeholder("https://open.feishu.cn/open-apis/bot/v2/hook/..."),
                ChannelField.of("secret", ChannelField.KIND_TEXT).secret()
                        .label("channel.field.feishuSignSecret")
        );
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        // hook 地址的最后一段就是口令，只留到 /hook/ 之前
        String webhook = webhookUrlOf(channelConfig);
        int hookAt = webhook.indexOf("/hook/");
        return hookAt < 0 ? webhook : webhook.substring(0, hookAt) + "/hook/***";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        String webhook = NotifierSupport.requireText(config, "webhook", channelConfig.getName());
        webhook = OutboundUrlGuard.requireAllowed(webhook, channelConfig.getName());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", NotifierSupport.formatMessage(rule, event));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", content);

        Object signSecret = config.get("secret");
        String secret = signSecret == null ? "" : String.valueOf(signSecret).trim();
        if (!secret.isEmpty() && !ChannelField.MASK.equals(secret)) {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            body.put("timestamp", timestamp);
            body.put("sign", sign(timestamp, secret));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "飞书机器人返回非 2xx: " + response.getStatusCode());
        }
        Integer code = readStatusCode(response.getBody());
        if (code == null || code != 0) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "飞书机器人拒绝消息: code=" + code + ", body=" + safeBody(response.getBody()));
        }
        log.info("飞书通知已发送: eventId={}, ruleId={}, signed={}", event.getId(), rule.getId(), body.containsKey("sign"));
    }

    private String sign(String timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
        } catch (Exception e) {
            // JDK 自带 HmacSHA256，走到这里只能是环境被裁剪过；签名算不出来就必须发不出去，不能静默降级成不签名
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "飞书签名计算失败: " + e.getMessage());
        }
    }

    private String webhookUrlOf(NotifyChannel channelConfig) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        return OutboundUrlGuard.requireAllowed(
                NotifierSupport.requireText(config, "webhook", channelConfig.getName()),
                channelConfig.getName());
    }

    /**
     * 飞书新老机器人响应体字段不同（新版 {@code code}，旧版 {@code StatusCode}），
     * 两个都认；都读不到就算失败 —— 认不出来还回"成功"是最坏的结果。
     */
    private Integer readStatusCode(String body) {
        if (body == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object code = parsed.get("code");
            if (code == null) {
                code = parsed.get("StatusCode");
            }
            return code instanceof Number ? ((Number) code).intValue() : null;
        } catch (Exception e) {
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
