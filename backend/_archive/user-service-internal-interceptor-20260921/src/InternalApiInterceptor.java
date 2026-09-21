package com.dataviz.user.config;

import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.common.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /user/internal/** 的入口闸门：这些端点免 JWT（auth-service 认证时手上没有 token），
 * 靠共享口令 X-Internal-Token 挡住横向调用。口令未配置时一律拒绝，不做"没配就放开"的降级。
 */
@Slf4j
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Internal-Token";

    @Value("${internal.api.token:}")
    private String expectedToken;

    @PostConstruct
    public void checkConfiguration() {
        if (!StringUtils.hasText(expectedToken)) {
            log.error("internal.api.token 未配置，/user/internal/** 将拒绝所有请求："
                    + "认证服务读不到用户，登录会失败。请在 auth-service 与 user-service 配成同一个值。");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!StringUtils.hasText(expectedToken)) {
            writeForbidden(response, "Internal API is not configured");
            return false;
        }
        String actual = request.getHeader(TOKEN_HEADER);
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
