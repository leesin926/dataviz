package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 短信通知：<b>当前实现不出短信</b>，一律抛异常留 FAILED 记录。
 * <p>
 * 短信必须有服务商凭据（AccessKey/签名/模板），这些既不在离线仓库里也不在本机配置里；
 * 拿不到凭据还回"成功"，等于让值班的人以为手机会响。
 */
@Component
public class SmsNotifier implements AlertNotifier {

    private static final String NOT_AVAILABLE =
            "短信通道尚未接入：缺少服务商凭据与 SDK（provider/signName/templateCode 已配置，但未接入发送）";

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public boolean deliverable() {
        return false;
    }

    @Override
    public List<ChannelField> fields() {
        return ChannelField.list(
                ChannelField.of("provider", ChannelField.KIND_SELECT).required()
                        .options("aliyun", "tencent", "huawei")
                        .defaultValue("aliyun").label("channel.field.provider"),
                ChannelField.of("accessKeyId", ChannelField.KIND_TEXT).label("channel.field.accessKeyId"),
                ChannelField.of("accessKeySecret", ChannelField.KIND_TEXT).secret().label("channel.field.accessKeySecret"),
                ChannelField.of("signName", ChannelField.KIND_TEXT).required().label("channel.field.signName")
                        .placeholder("数据可视化"),
                ChannelField.of("templateCode", ChannelField.KIND_TEXT).required().label("channel.field.templateCode")
                        .placeholder("SMS_123456789")
                // 收件号码（原先的 receivers）改由规则挂的通知组提供，见 NotifyTargets
        );
    }

    @Override
    public String targetKind() {
        return TARGET_MOBILE;
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        String recipients = targets.describe();
        return recipients.isEmpty() ? "（无收件人）" : recipients;
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        // 不先判收件人：这条路真正的原因是通道没接上，报"没有收件人"会把人支到错的地方去改
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, NOT_AVAILABLE);
    }
}
