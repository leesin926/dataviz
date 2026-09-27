package com.dataviz.admin.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 免登可读的全局配置键：唯一登记处。
 * <p>
 * 之前这份名单只在 {@code ConfigController} 里是一个私有 Set，而"能不能免登读"这件事现在有两个出口
 * （HTTP 白名单端点 + WS 推送通道），两份名单必然漂移，所以抽到这里。新增全局开关只需改这一个文件。
 */
public final class PublicConfigKeys {

    /** 键名常量：前端 {@code MOURNING_CONFIG_KEY} 与 uni 端 {@code MOURNING_CONFIG_KEY} 必须与此一致 */
    public static final String MOURNING_ENABLED = "screen.mourning.enabled";

    /**
     * 免登推送通道的握手路径。admin-service 那条网关路由没有 StripPrefix，
     * 所以网关侧与服务侧看到的是同一个完整路径（见 D38）。
     */
    public static final String PUSH_CHANNEL_PATH = "/api/admin/ws/public";

    private static final Set<String> WHITELIST = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(MOURNING_ENABLED)));

    private PublicConfigKeys() {
    }

    public static boolean isPublic(String configKey) {
        return configKey != null && WHITELIST.contains(configKey);
    }
}
