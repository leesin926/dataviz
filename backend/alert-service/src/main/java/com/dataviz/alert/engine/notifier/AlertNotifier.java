package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;

import java.util.Collections;
import java.util.List;

/**
 * 一种通知渠道一个实现。发送失败一律抛异常，由 {@code NotifyDispatcher} 记进 alert_notify_log ——
 * 静默"成功"是最坏的结果，因为它让值班的人以为通知发出去了。
 */
public interface AlertNotifier {

    /** 对应 notify_channel.type：EMAIL / SMS / WEBHOOK / DINGTALK / WECHAT / FEISHU */
    String channel();

    /**
     * 该渠道的配置项清单，管理端配置页据此渲染表单、保存侧据此校验。
     * 默认空列表是给"不需要配置"的实现留的余地；当前 6 个渠道都自己声明，
     * 因为<b>没有声明就等于界面上填不出、校验也无从下手</b>。
     * <p>
     * <b>这份清单里不再有收件人</b>：{@code to} / {@code receivers} / {@code mobiles} 从阶段 AQ 起
     * 由规则挂的通知组提供（原因见 {@link NotifyTargets} 的类注释），渠道配置只留出口与凭据。
     */
    default List<ChannelField> fields() {
        return Collections.emptyList();
    }

    /**
     * 这条通道<b>现在到底发不发得出去</b>。短信缺服务商凭据，配置照样能存、能校验，但发送一定是失败 ——
     * 界面上要提前显形，否则用户会把自己配的渠道当成"配坏了"。
     */
    default boolean deliverable() {
        return true;
    }

    /** 收件人形状：邮箱地址（EMAIL） */
    String TARGET_EMAIL = "email";
    /** 收件人形状：手机号（SMS 的收件人、钉钉/企业微信的 @ 对象） */
    String TARGET_MOBILE = "mobile";
    /** 收件人形状：没有"收件人"这个概念 —— 出口地址本身就是收件方（Webhook、无 @ 的群机器人） */
    String TARGET_NONE = "none";

    /**
     * 该渠道要哪一类收件人。它<b>不是装饰</b>：配置页的"测试发送"弹窗据此决定要不要给一个临时收件人输入框，
     * 规则表单据此决定通知组里"有没有邮箱地址"要不要提示。写在前端就等于再维护一份渠道词汇表，
     * 而这套形状是从这里导出的（跟 {@link #fields()} 同一个道理）。
     */
    default String targetKind() {
        return TARGET_NONE;
    }

    /**
     * @param channelConfig 该渠道在 notify_channel 表里的配置（config 字段是 JSON）—— <b>只含出口与凭据</b>
     * @param targets       这次要发给谁：由规则挂的通知组解析，解析不出来时回退渠道里存量的收件人键
     */
    void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets);

    /** 日志与告警记录里展示的接收方（webhook 地址 / 群机器人 / 收件人列表） */
    String recipientOf(NotifyChannel channelConfig, NotifyTargets targets);
}
