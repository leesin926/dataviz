package com.dataviz.alert.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertNotifyLog;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertNotifyLogMapper;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知派发：规则的 notify_channels 是渠道<b>类型</b>数组（如 {@code ["WEBHOOK","DINGTALK"]}），
 * 每个类型到 notify_channel 表里取实际配置，再交给对应 {@link AlertNotifier} 发送，
 * 成败都写 alert_notify_log。
 * <p>
 * 每个渠道单独 catch：一个渠道挂（邮件还没接 SMTP）不该让钉钉/钉钉群也一起不发。
 */
@Slf4j
@Component
public class NotifyDispatcher {

    private final Map<String, AlertNotifier> notifiers = new HashMap<>();
    private final NotifyChannelMapper notifyChannelMapper;
    private final AlertNotifyLogMapper alertNotifyLogMapper;
    private final ObjectMapper objectMapper;

    public NotifyDispatcher(List<AlertNotifier> notifierList,
                            NotifyChannelMapper notifyChannelMapper,
                            AlertNotifyLogMapper alertNotifyLogMapper,
                            ObjectMapper objectMapper) {
        for (AlertNotifier notifier : notifierList) {
            this.notifiers.put(notifier.channel().toUpperCase(), notifier);
        }
        this.notifyChannelMapper = notifyChannelMapper;
        this.alertNotifyLogMapper = alertNotifyLogMapper;
        this.objectMapper = objectMapper;
    }

    public void dispatch(AlertRule rule, AlertEvent event) {
        for (String type : parseChannels(rule.getNotifyChannels())) {
            String channel = type == null ? "" : type.trim().toUpperCase();
            if (!StringUtils.hasText(channel)) {
                continue;
            }
            String recipient = null;
            try {
                AlertNotifier notifier = notifiers.get(channel);
                if (notifier == null) {
                    throw new IllegalStateException("没有名为 " + channel + " 的通知实现");
                }
                NotifyChannel config = findEnabledChannel(channel);
                recipient = notifier.recipientOf(config);
                notifier.send(config, rule, event);
                logResult(event.getId(), channel, recipient, "SUCCESS", null);
            } catch (Exception e) {
                log.error("通知发送失败: channel={}, eventId={}, ruleId={}", channel, event.getId(), rule.getId(), e);
                logResult(event.getId(), channel, recipient, "FAILED", e.getMessage());
            }
        }
    }

    /**
     * 配置存在但渠道被停用也要留痕 —— 否则"为什么没收到告警"无从查起。
     */
    private NotifyChannel findEnabledChannel(String type) {
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyChannel::getType, type).orderByAsc(NotifyChannel::getId).last("LIMIT 1");
        NotifyChannel config = notifyChannelMapper.selectOne(wrapper);
        if (config == null) {
            throw new IllegalStateException("未配置 " + type + " 渠道");
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException(type + " 渠道已停用: " + config.getName());
        }
        return config;
    }

    private List<String> parseChannels(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.error("notify_channels 不是合法 JSON 数组: {}", json, e);
            return Collections.emptyList();
        }
    }

    private void logResult(Long eventId, String channel, String recipient, String status, String error) {
        AlertNotifyLog record = AlertNotifyLog.builder()
                .eventId(eventId)
                .channel(channel)
                .recipient(recipient)
                .status(status)
                .errorMessage(error)
                .createTime(LocalDateTime.now())
                .build();
        alertNotifyLogMapper.insert(record);
    }
}
