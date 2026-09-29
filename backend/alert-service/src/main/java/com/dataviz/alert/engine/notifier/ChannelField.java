package com.dataviz.alert.engine.notifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 一个渠道配置项的自述。管理端配置页**照这份描述渲染表单**，所以它就是渠道的接口契约：
 * 加一种渠道只改对应 {@link AlertNotifier#fields()}，前端不用动一行代码。
 * <p>
 * 三个字段是功能性的，不是装饰：
 * <ul>
 *   <li>{@code secret}：读取时一律替换成 {@link #MASK} 回给前端，保存时前端原样交回 {@code MASK} 或留空
 *       表示"沿用库里那份"。没有这套双向约定，"打开表单点保存"就会把群机器人 token 冲成空；</li>
 *   <li>{@code url}：保存时就用 {@link OutboundUrlGuard} 判一遍（发送侧那次判据仍在，两层不互斥）。
 *       放在保存侧是为了让填错的人当场看到红字，而不是等下一次告警触发；</li>
 *   <li>{@code labelKey}：文案键，翻译在前端（后端回中文键名会让管理端切英文时只剩半截界面）。
 *       前端没有这个键时按 {@code key} 原样显示，新旧渠道因此可以不同步上线。</li>
 * </ul>
 */
public final class ChannelField {

    /** 掩码占位值：读出去长这样，写回来长这样表示"不改" */
    public static final String MASK = "***";

    public static final String KIND_TEXT = "text";
    public static final String KIND_PASSWORD = "password";
    public static final String KIND_NUMBER = "number";
    public static final String KIND_SWITCH = "switch";
    public static final String KIND_LIST = "list";
    public static final String KIND_SELECT = "select";
    public static final String KIND_JSON = "json";
    public static final String KIND_TEXTAREA = "textarea";

    private final String key;
    private String kind;

    private boolean required;
    private boolean secret;
    private boolean url;
    private String labelKey;
    private String placeholder;
    private String defaultValue;
    private List<String> options = Collections.emptyList();

    private ChannelField(String key, String kind) {
        this.key = key;
        this.kind = kind;
        // 默认键名就是 labelKey：新渠道忘了配文案时，界面退化成显示 smtp / signName 而不是崩
        this.labelKey = "channel.field." + key;
    }

    public static ChannelField of(String key, String kind) {
        return new ChannelField(key, kind);
    }

    public ChannelField required() {
        this.required = true;
        return this;
    }

    public ChannelField secret() {
        this.secret = true;
        this.kind = KIND_PASSWORD;
        return this;
    }

    public ChannelField url() {
        this.url = true;
        return this;
    }

    public ChannelField label(String key) {
        this.labelKey = key;
        return this;
    }

    public ChannelField placeholder(String value) {
        this.placeholder = value;
        return this;
    }

    public ChannelField defaultValue(String value) {
        this.defaultValue = value;
        return this;
    }

    public ChannelField options(String... values) {
        this.options = Collections.unmodifiableList(Arrays.asList(values));
        return this;
    }

    public String getKey() {
        return key;
    }

    public String getKind() {
        return kind;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isSecret() {
        return secret;
    }

    public boolean isUrl() {
        return url;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getOptions() {
        return options;
    }

    /** 便于 notifier 里声明列表项 */
    static List<ChannelField> list(ChannelField... fields) {
        return Collections.unmodifiableList(new ArrayList<ChannelField>(Arrays.asList(fields)));
    }
}
