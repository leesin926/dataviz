package com.dataviz.common.security.interceptor;

import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.common.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部接口（约定路径 {@code /**／internal/**}）的统一闸门。
 * <p>
 * 这些端点免 JWT（调用方手上还没有 token），靠共享口令 {@code X-Internal-Token} 挡住横向调用。
 * 口令未配置时一律拒绝，不做"没配就放开"的降级 —— 意外公开一个能读密码哈希的端点，
 * 比登录暂时失败严重得多。
 */
@Slf4j
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    /** 内部路径被拦下时，配置问题只在真正有人调用时才报错，避免 16 个服务启动即刷 error */
    private volatile boolean configWarned;

    @Value("${internal.api.token:}")
    private String expectedToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!StringUtils.hasText(expectedToken)) {
            if (!configWarned) {
                configWarned = true;
                log.error("internal.api.token 未配置，内部接口将拒绝所有请求: {}（各服务必须配成同一个值）",
                        request.getRequestURI());
            }
            writeForbidden(response, "Internal API is not configured");
            return false;
        }
        String actual = request.getHeader(TOKEN_HEADER);
        // 常量时间比较：口令比对侧信道在这种端点上是有意义的（它能读到口令哈希）
        if (actual == null || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            log.warn("内部接口口令校验失败: {} {} from {}", request.getMethod(), request.getRequestURI(),
                    request.getRemoteAddr());
            writeForbidden(response, "Invalid internal token");
            return false;
        }
        return true;
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(R.fail(ErrorCode.FORBIDDEN.getCode(), message)));
    }
}
