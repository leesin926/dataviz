package com.dataviz.common.security.config;

import com.dataviz.common.security.interceptor.InternalApiInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 按约定给内部接口挂口令校验：路径中出现 {@code /internal/} 即受保护，各服务无需重复接线。
 * <p>
 * 与 {@link SecurityConfig} 的关系是互补而非重复：那边免掉 JWT（调用方没有 token），这边要求
 * {@code X-Internal-Token}，再由网关的 DENY_LIST 保证这些路径穿不出网关。三层缺一层都不安全。
 */
@Configuration
@RequiredArgsConstructor
public class InternalApiWebConfig implements WebMvcConfigurer {

    /** 约定优于配置：任何服务新增 /xxx/internal/** 端点都自动被拦，忘记配防护的失败方向是"多拦一道 403" */
    private static final String INTERNAL_PATH_PATTERN = "/**/internal/**";

    private final InternalApiInterceptor internalApiInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiInterceptor).addPathPatterns(INTERNAL_PATH_PATTERN);
    }
}
