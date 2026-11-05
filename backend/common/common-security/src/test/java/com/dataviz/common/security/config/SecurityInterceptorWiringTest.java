package com.dataviz.common.security.config;

import com.dataviz.common.redis.util.CacheHelper;
import com.dataviz.common.security.interceptor.AuthInterceptor;
import com.dataviz.common.security.interceptor.InternalApiInterceptor;
import com.dataviz.common.security.interceptor.PermissionInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拦截器接线：这两条路径约定就是安全边界本身，写错了不会报错、只会静默变宽。
 * ①{@code /**&#47;internal/**} 决定"哪些端点免 JWT 后还要口令闸门"；
 * ②{@code SecurityConfig.EXCLUDE_PATHS} 决定"免 JWT 白名单有多宽"。
 * 后者里 {@code /api/screen/share/*} 刻意用 /* 而不是 /**：AntPathMatcher 的 /** 连零段都匹配，
 * 会把"前缀本身"（/api/screen/share）和任意深度子路径一起免掉。这里用真路径把差异钉住。
 */
@ExtendWith(MockitoExtension.class)
class SecurityInterceptorWiringTest {

    @Mock
    private CacheHelper cacheHelper;

    /** 能读到 InterceptorRegistry 里注册的 MappedInterceptor（父类方法是 protected） */
    private static final class CapturingRegistry extends InterceptorRegistry {
        private List<Object> captured() {
            return getInterceptors();
        }
    }

    // ---------- InternalApiWebConfig：/**\internal/** ----------

    @Test
    @DisplayName("内部闸门只注册一个拦截器，且就是注入的那个实例")
    void should_registerInjectedInterceptor_when_configApplied() {
        // Given
        InternalApiInterceptor interceptor = new InternalApiInterceptor();
        CapturingRegistry registry = new CapturingRegistry();

        // When
        new InternalApiWebConfig(interceptor).addInterceptors(registry);

        // Then
        assertEquals(1, registry.captured().size());
        MappedInterceptor mapped = (MappedInterceptor) registry.captured().get(0);
        assertSame(interceptor, mapped.getInterceptor());
    }

    @Test
    @DisplayName("带 /internal/ 的路径（任意前缀、任意深度）都进闸门")
    void should_protect_when_pathContainsInternalSegment() {
        // Given
        MappedInterceptor mapped = internalMapped();

        // When/Then：matches=true 表示拦截器会作用到这条路径
        assertTrue(mapped.matches(request("/user/internal/sync")));
        assertTrue(mapped.matches(request("/api/datasource/internal/query")));
        assertTrue(mapped.matches(request("/model/internal/deep/one")));
        assertTrue(mapped.matches(request("/internal/x")));
    }

    @Test
    @DisplayName("非内部路径完全不经过闸门：按段比对，不做子串匹配")
    void should_leaveUntouched_when_pathIsBusinessRoute() {
        // Given
        MappedInterceptor mapped = internalMapped();

        // When/Then
        assertFalse(mapped.matches(request("/user/list")), "/user/list 不该被拦");
        assertFalse(mapped.matches(request("/dashboard/internal-report/list")),
                "段名 internal-report 不等于 internal");
        assertFalse(mapped.matches(request("/internal-api/list")), "前缀包含 internal 也不算");
    }

    @Test
    @DisplayName("路径正好以 /internal 结尾时同样被拦：Ant 的尾部 /** 连零段都匹配（闸门只可能更宽，方向安全）")
    void should_alsoGateBarePrefix_when_pathEndsAtInternal() {
        // Given
        MappedInterceptor mapped = internalMapped();

        // When/Then：与免登白名单刻意用 /* 的取舍相反，这里是"多拦一道"而不是"少拦一道"
        assertTrue(mapped.matches(request("/api/datasource/internal")), "/x/internal 也被拦");
        assertTrue(mapped.matches(request("/internal")), "裸 /internal 也被拦");
    }

    // ---------- SecurityConfig：免 JWT 白名单 ----------

    @Test
    @DisplayName("注册顺序是先认证后权限，两者都覆盖 /**")
    void should_registerAuthThenPermission_when_securityConfigApplied() {
        // Given
        CapturingRegistry registry = new CapturingRegistry();

        // When
        new SecurityConfig(cacheHelper).addInterceptors(registry);

        // Then
        assertEquals(2, registry.captured().size());
        assertInstanceOf(AuthInterceptor.class, ((MappedInterceptor) registry.captured().get(0)).getInterceptor());
        assertInstanceOf(PermissionInterceptor.class,
                ((MappedInterceptor) registry.captured().get(1)).getInterceptor());
        assertEquals("/**", ((MappedInterceptor) registry.captured().get(0)).getPathPatterns()[0]);
    }

    @Test
    @DisplayName("/api/screen/share/* 只免一段：分享链接本身免鉴权，裸前缀与两段路径仍要 token")
    void should_excludeOnlyOneSegment_when_sharePathWhitelist() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/api/screen/share/AbCd1234")), "一段 token 免鉴权");
        assertTrue(auth.matches(request("/api/screen/share")),
                "裸前缀不免鉴权：否则会落进 GET /{id} 处理器");
        assertTrue(auth.matches(request("/api/screen/share/AbCd1234/live")),
                "两段不免鉴权：/* 不是 /**");
    }

    @Test
    @DisplayName("同一批路径若写成 /** 就会一起被免掉——这正是白名单刻意不用 /** 的原因")
    void should_excludeEverythingBelow_when_patternWereRecursiveWildcard() {
        // Given：只用 /** 反例做对照
        MappedInterceptor recursive = new MappedInterceptor(
                new String[]{"/**"}, new String[]{"/api/screen/share/**"}, new AuthInterceptor(cacheHelper));

        // When/Then
        assertFalse(recursive.matches(request("/api/screen/share")));
        assertFalse(recursive.matches(request("/api/screen/share/AbCd1234/live")));
    }

    @Test
    @DisplayName("登录/注册/验证码等免登端点不进认证拦截器")
    void should_excludePublicAuthEndpoints_when_securityConfigApplied() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/auth/login")));
        assertFalse(auth.matches(request("/auth/captcha")));
        assertFalse(auth.matches(request("/auth/refresh-token")));
    }

    @Test
    @DisplayName("短信与 sso 端点按精确清单免登：多一段就仍然要 token（预先免登是危险方向）")
    void should_keepIntercepting_when_pathUnderWhitelistedAuthPrefix() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/auth/sms/send")));
        assertTrue(auth.matches(request("/auth/sms/admin-toggle")));
        assertTrue(auth.matches(request("/auth/sso")));
        assertTrue(auth.matches(request("/auth/refresh-token/rotate")));
    }

    @Test
    @DisplayName("服务间内部路径免掉 JWT（改由 X-Internal-Token 把关），业务路径不免")
    void should_excludeInternalConventions_when_securityConfigApplied() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/user/internal/sync")));
        assertFalse(auth.matches(request("/api/datasource/internal/query")));
        assertTrue(auth.matches(request("/user/list")));
        assertTrue(auth.matches(request("/dashboard/123")));
    }

    @Test
    @DisplayName("文档与探针端点免登，业务 API 不免")
    void should_excludeDocAndActuator_when_securityConfigApplied() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/swagger-ui/index.html")));
        assertFalse(auth.matches(request("/v3/api-docs/default")));
        assertFalse(auth.matches(request("/actuator/health")));
        assertTrue(auth.matches(request("/api/admin/users")));
    }

    @Test
    @DisplayName("图片直读与公开开关各只免一段：/api/file/view 本身仍要鉴权")
    void should_excludeOneSegmentOnly_when_fileAndAdminPublicPaths() {
        // Given
        MappedInterceptor auth = authMapped();

        // When/Then
        assertFalse(auth.matches(request("/api/file/view/a.png")));
        assertTrue(auth.matches(request("/api/file/view")));
        assertFalse(auth.matches(request("/api/admin/config/public/site")));
        assertTrue(auth.matches(request("/api/admin/config/public/site/logo")));
    }

    @Test
    @DisplayName("权限端点（非白名单）同时受认证与权限两个拦截器作用")
    void should_applyBothInterceptors_when_businessProtectedRoute() {
        // Given
        CapturingRegistry registry = new CapturingRegistry();
        new SecurityConfig(cacheHelper).addInterceptors(registry);

        // When
        MockHttpServletRequest request = request("/system/user/list");
        boolean authApplies = ((MappedInterceptor) registry.captured().get(0)).matches(request);
        boolean permissionApplies = ((MappedInterceptor) registry.captured().get(1)).matches(request);

        // Then
        assertTrue(authApplies);
        assertTrue(permissionApplies);
    }

    // ---------- helpers ----------

    private MappedInterceptor authMapped() {
        CapturingRegistry registry = new CapturingRegistry();
        new SecurityConfig(cacheHelper).addInterceptors(registry);
        return (MappedInterceptor) registry.captured().get(0);
    }

    private MappedInterceptor internalMapped() {
        CapturingRegistry registry = new CapturingRegistry();
        new InternalApiWebConfig(new InternalApiInterceptor()).addInterceptors(registry);
        return (MappedInterceptor) registry.captured().get(0);
    }

    private static void assertInstanceOf(Class<?> expected, Object actual) {
        assertEquals(expected, actual.getClass());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("");
        // DispatcherServlet 在匹配拦截器前一定会先解析并缓存 RequestPath，测试保持同一步
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }
}
