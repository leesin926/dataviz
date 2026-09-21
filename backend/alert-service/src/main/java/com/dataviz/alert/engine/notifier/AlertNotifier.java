package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;

/**
 * 一种通知渠道一个实现。发送失败一律抛异常，由 {@code NotifyDispatcher} 记进 alert_notify_log ——
 * 静默"成功"是最坏的结果，因为它让值班的人以为通知发出去了。
 */
public interface AlertNotifier {

    /** 对应 notify_channel.type：EMAIL / SMS / WEBHOOK / DINGTALK */
    String channel();

    /**
     * @param channelConfig 该渠道在 notify_channel 表里的配置（config 字段是 JSON）
     */
    void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event);

    /** 日志与告警记录里展示的接收方（webhook 地址 / 群机器人 / 收件人列表） */
    String recipientOf(NotifyChannel channelConfig);
}
