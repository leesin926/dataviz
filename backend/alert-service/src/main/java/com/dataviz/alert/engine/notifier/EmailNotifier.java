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
 * 邮件通知：<b>当前实现不出信</b>，一律抛异常，让 alert_notify_log 留下 FAILED 记录。
 * <p>
 * 离线 Maven 仓库里没有 {@code spring-boot-starter-mail}/jakarta.mail，本服务无法真正走 SMTP；
 * 与其 log 一行"已发送"骗过值班的人（原来的写法就是这个效果），不如明确失败并在响应里说清缺什么。
 * 补齐方式：能联网时引入 starter-mail + 一个 JavaMailSenderImpl，把 {@link #send} 换成真发送即可，
 * 渠道配置（smtp/port/from/to/ssl）已经按那个形状解析好了。
 */
@Component
public class EmailNotifier implements AlertNotifier {

    private static final String NOT_AVAILABLE =
            "邮件通道尚未接入：缺少可用的 SMTP 客户端依赖（spring-boot-starter-mail 不在离线仓库中）";

    private final ObjectMapper objectMapper;

    public EmailNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig) {
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        List<String> to = NotifierSupport.stringList(config, "to");
        return to.isEmpty() ? String.valueOf(config.get("to")) : String.join(",", to);
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event) {
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, NOT_AVAILABLE);
    }
}
