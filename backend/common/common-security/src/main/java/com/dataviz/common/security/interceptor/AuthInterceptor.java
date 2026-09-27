package com.dataviz.common.security.interceptor;

import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.common.core.util.JsonUtils;
import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.common.security.util.JwtHelper;
import com.dataviz.common.security.util.LoginSessionCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 认证拦截器 - 验证Token有效性
 */
@Slf4j
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final CacheHelper cacheHelper;
    private static final String TOKEN_PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();

        // OPTIONS请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头获取Token
        String authHeader = request.getHeader(SecurityContextHolder.getHeaderToken());
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)) {
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());

        try {
            // 解析Token
            Claims claims = JwtHelper.parseToken(token);
            String username = claims.getSubject();

            // 检查Redis中是否存在登录信息（防止token被盗用或用户已登出）
            String sessionKey = LoginSessionCache.key(username);
            LoginUser cachedUser = cacheHelper.get(sessionKey);
            if (cachedUser == null) {
                writeUnauthorizedResponse(response, "登录已过期，请重新登录");
                return false;
            }

            // 会话滑动续期：剩余不足 30 分钟时延长，避免活跃用户被强制下线
            Long sessionRemain = cacheHelper.getExpire(sessionKey, TimeUnit.SECONDS);
            if (sessionRemain != null && sessionRemain > 0 && sessionRemain < 1800) {
                cacheHelper.expire(sessionKey, LoginSessionCache.TTL_SECONDS, TimeUnit.SECONDS);
            }

            // 设置到ThreadLocal
            SecurityContextHolder.setLoginUser(cachedUser);

            // 刷新Token有效期（距离过期不足30分钟时刷新）
            long remainingSeconds = JwtHelper.getRemainingExpiration(token);
            if (remainingSeconds < 1800) {
                String newToken = JwtHelper.refreshToken(token);
                response.setHeader("New-Token", newToken);
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            writeUnauthorizedResponse(response, ErrorCode.TOKEN_EXPIRED.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            writeUnauthorizedResponse(response, ErrorCode.TOKEN_INVALID.getMessage());
            return false;
        } finally {
            // 注意：不在这里清除ThreadLocal，而是在afterCompletion中清除
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContextHolder.clear();
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> result = com.dataviz.common.core.result.R.fail(ErrorCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(JsonUtils.toJson(result));
    }
}
