package com.dataviz.common.security.util;

import com.dataviz.common.redis.util.CacheHelper;
import org.springframework.util.StringUtils;

/**
 * 登录态缓存键的唯一口径。
 * <p>
 * 这份快照由 auth-service 在登录时写入、由各服务的 {@code AuthInterceptor} 读取，
 * 键名只要有一处拼错，症状就是"改了权限/禁了账号但会话照旧"——它不会报错，只会静默失效。
 * 所以键前缀、TTL、驱逐动作都收在这里，不允许再有任何第二份字面量。
 * </p>
 */
public final class LoginSessionCache {

    private static final String KEY_PREFIX = "login:user:";

    /** 会话快照初始 TTL；拦截器的滑动续期用的也是这个值 */
    public static final long TTL_SECONDS = 7200L;

    private LoginSessionCache() {
    }

    public static String key(String username) {
        return KEY_PREFIX + username;
    }

    /**
     * 驱逐某个用户的登录态快照：角色/权限变更、账号停用、口令重置、资料改名后都必须调它，
     * 否则在 Redis 里的那份 {@code LoginUser} 会一直被拦截器当作最新状态使用。
     */
    public static void evict(CacheHelper cacheHelper, String username) {
        if (cacheHelper == null || !StringUtils.hasText(username)) {
            return;
        }
        cacheHelper.delete(key(username));
    }
}
