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
import org.springframework.core.annotation.AnnotatedElementUtils;
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

    /** 与前端 {@code PermissionManager.matchPermission} 同一套通配语义，两端必须一起改 */
    private static final String ALL = "*";
    private static final String PREFIX_WILDCARD_SUFFIX = ":*";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 方法级优先，缺省回落到类级：@Target 里声明了 TYPE，回落不实现就是"标在类上静默不生效"
        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresPermission.class);
        }
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
            for (String required : requiredPermissions) {
                if (!matches(userPermissions, required)) {
                    return false;
                }
            }
            return true;
        }
        for (String required : requiredPermissions) {
            if (matches(userPermissions, required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用户持有的权限码允许是 {@code *} 或 {@code system:*} 这类前缀通配（与前端同语义）。
     * 注意通配按字符串前缀截断，所以 {@code sys:*} 也会放行 {@code system:user:list} —— 这是两端共有的既有行为，
     * 要收紧必须同时改前端，否则会出现"菜单看得见、接口 403"。
     */
    private boolean matches(Set<String> userPermissions, String required) {
        if (!StringUtils.hasText(required)) {
            return false;
        }
        if (userPermissions.contains(required)) {
            return true;
        }
        for (String held : userPermissions) {
            if (!StringUtils.hasText(held)) {
                continue;
            }
            if (ALL.equals(held)) {
                return true;
            }
            if (held.endsWith(PREFIX_WILDCARD_SUFFIX)
                    && required.startsWith(held.substring(0, held.length() - PREFIX_WILDCARD_SUFFIX.length()))) {
                return true;
            }
        }
        return false;
    }

    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> result = com.dataviz.common.core.result.R.fail(ErrorCode.FORBIDDEN.getCode(), message);
        response.getWriter().write(JsonUtils.toJson(result));
    }
}
