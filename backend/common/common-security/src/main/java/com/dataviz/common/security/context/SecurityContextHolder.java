package com.dataviz.common.security.context;

import com.dataviz.common.security.model.LoginUser;

/**
 * 安全上下文持有者 - 基于ThreadLocal存储当前登录用户信息
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<LoginUser> USER_CONTEXT = new ThreadLocal<>();
    private static final String HEADER_TOKEN = "Authorization";
    private static final String HEADER_TENANT = "X-Tenant-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";

    private SecurityContextHolder() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 设置当前登录用户
     */
    public static void setLoginUser(LoginUser loginUser) {
        USER_CONTEXT.set(loginUser);
    }

    /**
     * 获取当前登录用户
     */
    public static LoginUser getLoginUser() {
        return USER_CONTEXT.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUsername() : null;
    }

    /**
     * 获取当前租户ID
     */
    public static String getTenantId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getTenantId() : null;
    }

    /**
     * 判断是否已登录
     */
    public static boolean isAuthenticated() {
        return getLoginUser() != null;
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        USER_CONTEXT.remove();
    }

    public static String getHeaderToken() {
        return HEADER_TOKEN;
    }

    public static String getHeaderTenant() {
        return HEADER_TENANT;
    }

    public static String getHeaderUserId() {
        return HEADER_USER_ID;
    }

    public static String getHeaderUsername() {
        return HEADER_USERNAME;
    }
}
