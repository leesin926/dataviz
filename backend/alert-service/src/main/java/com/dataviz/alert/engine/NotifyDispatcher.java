package com.dataviz.alert.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertNotifyLog;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertNotifyLogMapper;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.dataviz.alert.service.AlertNotifyTargetService;
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
 * 每个类型到 notify_channel 表里取实际配置（<b>出口</b>），收件人则由规则挂的通知组解析（<b>发给谁</b>），
 * 再交给对应 {@link AlertNotifier} 发送，成败都写 alert_notify_log。
 * <p>
 * 每个渠道单独 catch：一个渠道挂（短信还没接服务商凭据）不该让钉钉/webhook 也一起不发。
 */
@Slf4j
@Component
public class NotifyDispatcher {

    private final Map<String, AlertNotifier> notifiers = new HashMap<>();
    private final NotifyChannelMapper notifyChannelMapper;
    private final AlertNotifyLogMapper alertNotifyLogMapper;
    private final ObjectMapper objectMapper;
    private final AlertNotifyTargetService targetService;

    public NotifyDispatcher(List<AlertNotifier> notifierList,
                            NotifyChannelMapper notifyChannelMapper,
                            AlertNotifyLogMapper alertNotifyLogMapper,
                            ObjectMapper objectMapper,
                            AlertNotifyTargetService targetService) {
        for (AlertNotifier notifier : notifierList) {
            this.notifiers.put(notifier.channel().toUpperCase(), notifier);
        }
        this.notifyChannelMapper = notifyChannelMapper;
        this.alertNotifyLogMapper = alertNotifyLogMapper;
        this.objectMapper = objectMapper;
        this.targetService = targetService;
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
                // 收件人按"这一条规则挂的通知组"解析，所以同一条 EMAIL 渠道可以给不同规则发不同人
                NotifyTargets targets = targetService.resolveTargets(rule, config);
                recipient = notifier.recipientOf(config, targets);
                notifier.send(config, rule, event, targets);
                logResult(event.getId(), channel, recipient, "SUCCESS", null);
            } catch (Exception e) {
                log.error("通知发送失败: channel={}, eventId={}, ruleId={}", channel, event.getId(), rule.getId(), e);
                logResult(event.getId(), channel, recipient, "FAILED", e.getMessage());
            }
        }
    }

    /**
     * 每种类型取<b>启用中的</b> id 最小那条。
     * <p>
     * 原先这里没有 enabled 条件：查出 id 最小的那一条，再看它启没停用。于是"把第一条停用、换成新配的第二条"
     * 这个操作不会生效 —— 派发仍然撞在第一条停用记录上，日志写"渠道已停用"，而用户在配置页看到的是
     * "我这条是启用状态"，配了、启用了、就是收不到。表上没有 type 唯一键，多条同类型是合法数据，
     * 所以判据必须按"可用"来筛，而不是按"存在"。
     * <p>
     * 一条都没启用时才回退查任意一条，是为了让日志说清到底发生了什么：
     * 完全没有这一类型的记录（"未配置 X 渠道"）和有记录但都停着（"X 渠道已停用: 名称"）是两回事。
     */
    private NotifyChannel findEnabledChannel(String type) {
        NotifyChannel enabled = findChannel(type, true);
        if (enabled != null) {
            return enabled;
        }
        NotifyChannel disabled = findChannel(type, false);
        if (disabled == null) {
            throw new IllegalStateException("未配置 " + type + " 渠道");
        }
        throw new IllegalStateException(type + " 渠道已停用: " + disabled.getName());
    }

    private NotifyChannel findChannel(String type, boolean enabledOnly) {
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyChannel::getType, type);
        if (enabledOnly) {
            wrapper.eq(NotifyChannel::getEnabled, true);
        }
        wrapper.orderByAsc(NotifyChannel::getId).last("LIMIT 1");
        return notifyChannelMapper.selectOne(wrapper);
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
