package com.dataviz.common.log.util;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志工具类
 */
@Slf4j
public final class LogUtils {

    private LogUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 获取客户端真实IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (isUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
        }

        // 对于多个代理的情况，第一个IP为客户端真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 判断IP是否未知
     */
    private static boolean isUnknown(String ip) {
        return ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip);
    }

    /**
     * 构建操作日志消息
     */
    public static String buildLogMessage(String module, String operType, String description, boolean success) {
        return String.format("[%s] %s - %s (%s)", module, operType, description, success ? "成功" : "失败");
    }

    /**
     * 获取堆栈信息
     */
    public static String getStackTrace(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        StackTraceElement[] elements = e.getStackTrace();
        int maxLines = Math.min(elements.length, 10);
        for (int i = 0; i < maxLines; i++) {
            sb.append("\tat ").append(elements[i]).append("\n");
        }
        return sb.toString();
    }
}
