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
     * "没有收件人"要说清是哪一种没有 —— 三种情况的下一步动作完全不同，
     * 只会说"收件人为空"的话，值班人会去改渠道配置（那里已经没有收件人这一栏了）。
     * <ul>
     *   <li>{@code source=GROUP}：组挂上了，但组里没人有这个渠道要的地址（或那些人全被停用）⇒ 去补联系人；</li>
     *   <li>{@code source=NONE}：规则压根没挂组，渠道里也没有存量的历史收件人 ⇒ 去挂组；</li>
     *   <li>合成规则（配置页的"测试发送"）：没有规则可挂 ⇒ 在弹窗里填一个临时收件人。</li>
     * </ul>
     */
    static String noRecipients(AlertRule rule, NotifyTargets targets, String addressLabel) {
        if (rule.getId() == null) {
            return "测试发送需要临时收件人：请在弹窗里填至少一个" + addressLabel;
        }
        if (targets != null && targets.source() == NotifyTargets.Source.GROUP) {
            return "规则挂的通知组里没有人有可用" + addressLabel + "（联系人未填该地址，或已被停用）: 规则ID "
                    + rule.getId() + "「" + rule.getName() + "」";
        }
        return "规则未挂通知组、渠道里也没有存量" + addressLabel + "：请先在告警规则里选择通知组（规则ID "
                + rule.getId() + "「" + rule.getName() + "」）";
    }

    /**
     * 通知正文必须自带判定依据（当前值 vs 阈值），否则值班人收到"出告警了"也不知道要不要动手。
     * <p>
     * 刻意不放 metricExpression：它现在是一条 SQL，把查询语句贴进群聊/邮件既读不懂也泄漏表结构。
     * <p>
     * 规则 ID 与"当前值/阈值"这一段是<b>条件输出</b>的：渠道配置页的"测试发送"没有真实规则和取值，
     * 无脑拼上去就是"规则ID: null / 阈值: null"，收件人第一眼会以为告警系统坏了。真实告警两个值都在，
     * 这条改动不影响正式通知的正文。
     */
    static String formatMessage(AlertRule rule, AlertEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(event.getSeverity()).append("] ").append(rule.getName());
        if (rule.getId() != null) {
            sb.append(String.format("%n规则ID: %s", rule.getId()));
        }
        if (event.getTriggerValue() != null && rule.getThreshold() != null) {
            sb.append(String.format("%n当前值: %s %s 阈值: %s",
                    event.getTriggerValue(), rule.getCondition(), rule.getThreshold()));
        }
        sb.append(String.format("%n详情: %s", event.getMessage()));
        return sb.toString();
    }
}
