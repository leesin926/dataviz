package com.dataviz.common.security.interceptor;

import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.common.core.util.JsonUtils;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * 权限拦截器 - 检查用户是否有指定权限
 */
@Slf4j
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return true;
        }

        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        if (loginUser == null) {
            writeForbiddenResponse(response, "用户未登录");
            return false;
        }

        // 超级管理员拥有所有权限
        if (loginUser.isSuperAdmin()) {
            return true;
        }

        String[] requiredPermissions = annotation.value();
        RequiresPermission.Logical logical = annotation.logical();

        if (requiredPermissions.length == 0) {
            return true;
        }

        Set<String> userPermissions = loginUser.getPermissions();
        boolean hasPermission = checkPermission(userPermissions, requiredPermissions, logical);

        if (!hasPermission) {
            log.warn("用户 {} 无权限访问 {}，需要权限: {}",
                    loginUser.getUsername(), request.getRequestURI(), Arrays.toString(requiredPermissions));
            writeForbiddenResponse(response, ErrorCode.FORBIDDEN.getMessage());
            return false;
        }

        return true;
    }

    /**
     * 检查权限
     */
    private boolean checkPermission(Set<String> userPermissions, String[] requiredPermissions, RequiresPermission.Logical logical) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        if (logical == RequiresPermission.Logical.AND) {
            return Arrays.stream(requiredPermissions).allMatch(userPermissions::contains);
        } else {
            return Arrays.stream(requiredPermissions).anyMatch(userPermissions::contains);
        }
    }

    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> result = com.dataviz.common.core.result.R.fail(ErrorCode.FORBIDDEN.getCode(), message);
        response.getWriter().write(JsonUtils.toJson(result));
    }
}
