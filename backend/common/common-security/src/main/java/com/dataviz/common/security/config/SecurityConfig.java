package com.dataviz.common.security.config;

import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.interceptor.AuthInterceptor;
import com.dataviz.common.security.interceptor.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全配置类 - 注册拦截器
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(DispatcherServlet.class)
public class SecurityConfig implements WebMvcConfigurer {

    private final CacheHelper cacheHelper;

    /**
     * 不需要认证的路径
     */
    private static final String[] EXCLUDE_PATHS = {
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            "/auth/refresh-token",
            "/auth/refreshToken",
            // 第三方登录（auth-service）。网关侧写的是 /api/auth/sso/**，本服务侧因 StripPrefix=1 路径已去掉 /api（见 D38）。
            // 这里刻意逐条列举而非用通配：新增 sso 端点时若漏配，失败方向是"多拦一道 401"，而不是"意外变成免登公开接口"。
            "/auth/sso/login",
            "/auth/sso/callback",
            // 服务间内部接口：约定路径含 /internal/，调用方手上没有 JWT，必须免掉本拦截器。
            // 真正的闸门是 common-security 里的 InternalApiInterceptor（X-Internal-Token，未配置即拒绝），
            // 外加网关 DENY_LIST 保证这些路径穿不出网关 —— 三层缺一层都不安全。
            // 前缀按各服务实际看到的路径写：user-service 走 StripPrefix=1 故不带 /api，datasource-service 不带 StripPrefix 故带 /api（见 D38）。
            "/user/internal/**",
            "/api/datasource/internal/**",
            "/api/model/internal/**",
            // 大屏分享链接（screen-service 提供，其余服务无此路由）
            "/api/screen/share/**",
            // 图片直读（file-service，仅 image/*）与免登全局开关（admin-service，仅白名单 key）
            "/api/file/view/**",
            "/api/admin/config/public/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/doc.html",
            "/favicon.ico",
            "/error",
            "/actuator/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证拦截器
        registry.addInterceptor(new AuthInterceptor(cacheHelper))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATHS);

        // 权限拦截器
        registry.addInterceptor(new PermissionInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATHS);
    }
}
