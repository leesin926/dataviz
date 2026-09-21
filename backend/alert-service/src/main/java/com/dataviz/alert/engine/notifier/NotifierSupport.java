package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通知器共用的两件小事：解析渠道配置 JSON、拼一条人类可读的告警正文。
 */
final class NotifierSupport {

    private NotifierSupport() {
    }

    static Map<String, Object> parseConfig(ObjectMapper mapper, NotifyChannel channel) {
        if (channel.getConfig() == null || channel.getConfig().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "通知渠道未配置参数: " + channel.getName() + "(" + channel.getType() + ")");
        }
        try {
            return mapper.readValue(channel.getConfig(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "通知渠道配置不是合法 JSON: " + channel.getName());
        }
    }

    static String requireText(Map<String, Object> config, String key, String channelName) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "通知渠道 " + channelName + " 缺少必填配置: " + key);
        }
        return String.valueOf(value).trim();
    }

    static List<String> stringList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    /**
     * 通知正文必须自带判定依据（当前值 vs 阈值），否则值班人收到"出告警了"也不知道要不要动手。
     * <p>
     * 刻意不放 metricExpression：它现在是一条 SQL，把查询语句贴进群聊/邮件既读不懂也泄漏表结构。
     */
    static String formatMessage(AlertRule rule, AlertEvent event) {
        return String.format("[%s] %s%n规则ID: %s%n当前值: %s %s 阈值: %s%n详情: %s",
                event.getSeverity(),
                rule.getName(),
                rule.getId(),
                event.getTriggerValue(),
                rule.getCondition(),
                rule.getThreshold(),
                event.getMessage());
    }
}
