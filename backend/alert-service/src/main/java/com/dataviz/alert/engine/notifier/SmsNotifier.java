package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    private final ObjectMapper objectMapper;

    public SmsNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        List<String> receivers = NotifierSupport.stringList(config, "receivers");
        return receivers.isEmpty() ? String.valueOf(config.get("receivers")) : String.join(",", receivers);
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event) {
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, NOT_AVAILABLE);
    }
}
