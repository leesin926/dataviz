package com.dataviz.alert.engine.notifier;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 邮件通知：按渠道配置即时搭一个 {@link JavaMailSenderImpl} 真发信。
 * <p>
 * <b>为什么不注入现成的 MailSender bean</b>：{@code spring.mail.*} 是全局一份，而这一路的服务器、端口、
 * 加密方式、账号口令都在 {@code notify_channel.config} 里，每种渠道可以有若干条记录，
 * 且 JavaMail 的 Session 一旦按属性建好就缓存住了 —— 复用单例会发成"第一条保存的配置"那台服务器。
 * 通知量级用不到连接池，每次现搭反而没有陈旧状态。
 * <p>
 * 加密三态（{@code ssl} 隐式 TLS / {@code starttls} 升级 / {@code none} 明文）而不是一个 SSL 开关，
 * 是因为 465 与 587 的差别只在握手时机，用一个布尔值表达不了 25 端口匿名中继那种合法场景。
 * 老数据里只有一个 {@code ssl} 布尔键（本轮之前的字段契约），读不到 security 时按它和端口推断，
 * 免得升级后原来能发的邮件突然变成明文连接。
 */
@Component
public class EmailNotifier implements AlertNotifier {

    private static final String SECURITY_SSL = "ssl";
    private static final String SECURITY_STARTTLS = "starttls";
    private static final String SECURITY_NONE = "none";

    private static final int DEFAULT_PORT = 465;
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private final ObjectMapper objectMapper;

    public EmailNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public List<ChannelField> fields() {
        return ChannelField.list(
                ChannelField.of("smtp", ChannelField.KIND_TEXT).required().label("channel.field.smtp")
                        .placeholder("smtp.example.com"),
                ChannelField.of("port", ChannelField.KIND_NUMBER).defaultValue(String.valueOf(DEFAULT_PORT))
                        .label("channel.field.port"),
                ChannelField.of("security", ChannelField.KIND_SELECT).options(SECURITY_SSL, SECURITY_STARTTLS, SECURITY_NONE)
                        .defaultValue(SECURITY_SSL).label("channel.field.security"),
                ChannelField.of("username", ChannelField.KIND_TEXT).label("channel.field.username"),
                ChannelField.of("password", ChannelField.KIND_TEXT).secret().label("channel.field.password"),
                ChannelField.of("from", ChannelField.KIND_TEXT).required().label("channel.field.from")
                        .placeholder("alert@example.com"),
                // 收件人（原先的 to）不在这张表单里：它按规则挂的通知组解析，见 NotifyTargets
                ChannelField.of("subject", ChannelField.KIND_TEXT).label("channel.field.subject")
                        .placeholder("【数据告警】"),
                // SMTP 连不上时 JavaMail 默认等到天荒地老，而派发是串在告警链路上的线程里做的
                ChannelField.of("timeout", ChannelField.KIND_NUMBER).defaultValue(String.valueOf(DEFAULT_TIMEOUT_MS))
                        .label("channel.field.timeout"),
                ChannelField.of("trustHosts", ChannelField.KIND_TEXT).label("channel.field.trustHosts")
                        .placeholder("*")
        );
    }

    @Override
    public String targetKind() {
        return TARGET_EMAIL;
    }

    @Override
    public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
        String recipients = targets.describe();
        return recipients.isEmpty() ? "（无收件人）" : recipients;
    }

    @Override
    public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
        String name = channelConfig.getName();
        Map<String, Object> config = NotifierSupport.parseConfig(objectMapper, channelConfig);
        String host = NotifierSupport.requireText(config, "smtp", name);
        String from = NotifierSupport.requireText(config, "from", name);
        List<String> to = targets.emails();
        if (to.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    NotifierSupport.noRecipients(rule, targets, "邮箱地址"));
        }
        int port = intValue(config, "port", DEFAULT_PORT);
        String security = securityOf(config, name, port);
        String username = text(config, "username");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        if (username != null) {
            sender.setUsername(username);
            sender.setPassword(text(config, "password"));
        }
        Properties props = sender.getJavaMailProperties();
        int timeout = intValue(config, "timeout", DEFAULT_TIMEOUT_MS);
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.writetimeout", String.valueOf(timeout));
        if (SECURITY_SSL.equals(security)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (SECURITY_STARTTLS.equals(security)) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        String trustHosts = text(config, "trustHosts");
        if (trustHosts != null && !SECURITY_NONE.equals(security)) {
            props.put("mail.smtp.ssl.trust", trustHosts);
        }

        MimeMessageHelper helper;
        try {
            helper = new MimeMessageHelper(sender.createMimeMessage(), "UTF-8");
            helper.setFrom(from);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subjectOf(config, rule, event));
            helper.setText(NotifierSupport.formatMessage(rule, event), false);
        } catch (MessagingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "邮件写不出来（多半是地址格式不对）: " + rootMessage(e) + " / from=" + from + ", to=" + String.join(",", to));
        }
        try {
            sender.send(helper.getMimeMessage());
        } catch (Exception e) {
            // 原话在这里最值钱：值班人要么改口令、要么改端口，靠"发送失败"四个字都做不了判断
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "邮件发送失败: " + rootMessage(e) + "（" + host + ":" + port + "，" + security + "，超时 " + timeout + "ms）");
        }
    }

    /**
     * 加密方式。读不到 security 时按老配置（ssl 布尔键）和端口推断，避免字段契约换形状之后
     * 一条本来能发的记录突然改走明文连接。
     */
    private String securityOf(Map<String, Object> config, String channelName, int port) {
        String declared = text(config, "security");
        if (declared != null) {
            String value = declared.toLowerCase();
            if (SECURITY_SSL.equals(value) || SECURITY_STARTTLS.equals(value) || SECURITY_NONE.equals(value)) {
                return value;
            }
            throw new BizException(ErrorCode.BAD_REQUEST, channelName + " 的 security 只能是 ssl、starttls、none，当前是: " + declared);
        }
        Object legacy = config.get("ssl");
        if (legacy != null) {
            String flag = String.valueOf(legacy).trim();
            boolean on = "true".equalsIgnoreCase(flag) || "1".equals(flag) || "on".equalsIgnoreCase(flag);
            return on ? SECURITY_SSL : (port == 587 ? SECURITY_STARTTLS : SECURITY_NONE);
        }
        if (port == 587) {
            return SECURITY_STARTTLS;
        }
        return port == 25 ? SECURITY_NONE : SECURITY_SSL;
    }

    private String subjectOf(Map<String, Object> config, AlertRule rule, AlertEvent event) {
        String title = "[" + event.getSeverity() + "] " + rule.getName();
        String prefix = text(config, "subject");
        return prefix == null ? title : prefix + " " + title;
    }

    private String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int intValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, key + " 必须是数字，当前是: " + value);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.trim().isEmpty() ? cursor.getClass().getSimpleName() : message.trim();
    }
}
