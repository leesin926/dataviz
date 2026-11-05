package com.dataviz.common.security.interceptor;

import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限拦截器。判据集中在三处：注解解析优先级（方法级压过类级，回落必须实现）、
 * 通配语义（* 与 system:*，以及与前端同源的"前缀截断"宽松匹配）、拒绝时的响应形状（403 + 统一信封）。
 * 通配按字符串前缀截断，所以 {@code sys:*} 也会放行 {@code system:user:list} —— 这条既有行为被钉住，
 * 收紧必须同时改前端，否则出现"菜单看得见、接口 403"。
 */
class PermissionInterceptorTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor();
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ---------- 是否进入校验 ----------

    @Test
    @DisplayName("非 Controller 方法（静态资源/Resource 处理器）一律放行")
    void should_passThrough_when_handlerIsNotHandlerMethod() throws Exception {
        // Given
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, new Object());

        // Then
        assertTrue(passed);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    @DisplayName("方法上没有 @RequiresPermission 且类上也没有 → 不鉴权")
    void should_passThrough_when_noAnnotationAtAll() throws Exception {
        // Given/When
        boolean passed = interceptor.preHandle(request(), utf8Response(),
                handler(PlainController.class, "bare"));

        // Then
        assertTrue(passed);
    }

    @Test
    @DisplayName("权限值为空数组时视为不需要权限")
    void should_passThrough_when_requiredPermissionsEmpty() throws Exception {
        // Given
        login("bob", roles("user"), permissions("anything:else"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "noPermission")));
    }

    // ---------- 注解解析优先级 ----------

    @Test
    @DisplayName("方法级注解压过类级：只给类级权限仍然 403")
    void should_useMethodLevelOnly_when_bothLevelsAnnotated() throws Exception {
        // Given：类级 class:read，方法级 method:write
        login("bob", roles("user"), permissions("class:read"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "methodLevel"));

        // Then：两级注解不做并集
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("方法级权限命中即放行")
    void should_pass_when_methodLevelPermissionHeld() throws Exception {
        // Given
        login("bob", roles("user"), permissions("method:write"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "methodLevel")));
    }

    @Test
    @DisplayName("方法无注解时回落到类级注解（不实现就是「标在类上静默不生效」）")
    void should_fallBackToClassLevel_when_methodHasNoAnnotation() throws Exception {
        // Given
        login("bob", roles("user"), permissions("class:read"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "inheritsClassLevel")));
    }

    @Test
    @DisplayName("类级回落同样会拒：缺 class:read 就 403")
    void should_denyByClassLevel_when_classPermissionMissing() throws Exception {
        // Given
        login("bob", roles("user"), permissions("totally:other"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "inheritsClassLevel"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    // ---------- 登录态与超管 ----------

    @Test
    @DisplayName("ThreadLocal 里没有登录用户 → 403「用户未登录」而不是 401")
    void should_write403_when_loginUserMissing() throws Exception {
        // Given
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "methodLevel"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "用户未登录");
    }

    @Test
    @DisplayName("超级管理员直接放行，即使一条权限都没有")
    void should_passSuperAdmin_when_permissionsEmpty() throws Exception {
        // Given
        login("root", roles("super_admin"), Collections.<String>emptySet());

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "bothRequired")));
    }

    // ---------- 通配与逻辑 ----------

    @Test
    @DisplayName("精确权限码命中即放行，多余的其他权限项不影响判定")
    void should_pass_when_exactPermissionHeld() throws Exception {
        // Given
        login("bob", roles("user", "reporter"), permissions("system:user:list", "dashboard:view"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "systemUserList")));
    }

    @Test
    @DisplayName("OR 逻辑：命中任意一个即放行")
    void should_pass_when_orLogicHitsOneOfTwo() throws Exception {
        // Given
        login("bob", roles("user"), permissions("order:export"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "eitherRequired")));
    }

    @Test
    @DisplayName("OR 逻辑：两个都不命中才 403")
    void should_deny_when_orLogicMissesBoth() throws Exception {
        // Given
        login("bob", roles("user"), permissions("order:read:partial"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "eitherRequired"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("AND 逻辑：两个都命中才放行")
    void should_pass_when_andLogicHoldsAll() throws Exception {
        // Given
        login("bob", roles("user"), permissions("order:read", "order:export"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "bothRequired")));
    }

    @Test
    @DisplayName("AND 逻辑：缺一个就 403")
    void should_deny_when_andLogicMissingOne() throws Exception {
        // Given
        login("bob", roles("user"), permissions("order:read"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "bothRequired"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("通配 * 放行一切权限码")
    void should_passAll_when_userHoldsGlobalWildcard() throws Exception {
        // Given
        login("bob", roles("ops"), permissions("*"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "bothRequired")));
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "methodLevel")));
    }

    @Test
    @DisplayName("前缀通配 system:* 放行 system:user:list")
    void should_pass_when_userHoldsPrefixWildcard() throws Exception {
        // Given
        login("bob", roles("admin"), permissions("system:*"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "systemUserList")));
    }

    @Test
    @DisplayName("前缀通配按字符串截断：sys:* 也放行 system:user:list（既有宽松行为，钉住现状）")
    void should_pass_when_prefixWildcardIsPartialWord() throws Exception {
        // Given
        login("bob", roles("admin"), permissions("sys:*"));

        // When/Then
        assertTrue(interceptor.preHandle(request(), utf8Response(), handler(Ctl.class, "systemUserList")));
    }

    @Test
    @DisplayName("前缀通配不会跨段误伤：system:* 挡不住 user:list")
    void should_deny_when_prefixWildcardDoesNotMatchHead() throws Exception {
        // Given
        login("bob", roles("admin"), permissions("system:*"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "userList"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("*:* 不是通配：只有裸 * 与 xxx:* 两种形态生效")
    void should_deny_when_wildcardShapeIsStarColonStar() throws Exception {
        // Given
        login("bob", roles("admin"), permissions("*:*"));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "userList"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    // ---------- 空权限集合 ----------

    @Test
    @DisplayName("权限集合为 null 时按拒绝处理（而不是放行）")
    void should_deny_when_permissionSetNull() throws Exception {
        // Given
        login("bob", roles("user"), null);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "methodLevel"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("用户持有的空白/ null 权限项被跳过，不 NPE 也不放行")
    void should_skipBlankHeldPermissions_when_setContainsBlankEntries() throws Exception {
        // Given
        Set<String> held = new HashSet<>();
        held.add("");
        held.add(null);
        login("bob", roles("user"), held);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "methodLevel"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    @Test
    @DisplayName("注解里写了空白权限码时一律判不通过（fail closed）")
    void should_deny_when_requiredPermissionIsBlank() throws Exception {
        // Given
        login("bob", roles("user"), permissions("  "));
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request(), response, handler(Ctl.class, "blankPermission"));

        // Then
        assertFalse(passed);
        assertForbidden(response, "无权限访问");
    }

    // ---------- 拒绝响应形状 ----------

    @Test
    @DisplayName("拒绝写的是 403 + application/json;charset=UTF-8 + 信封 code=403")
    void should_writeJsonEnvelope_when_denied() throws Exception {
        // Given：已登录但缺 method:write
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = utf8Response();
        login("bob", roles("user"), permissions("other:read"));

        // When
        boolean passed = interceptor.preHandle(request, response, handler(Ctl.class, "methodLevel"));

        // Then
        assertFalse(passed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        assertTrue(response.getContentType().contains("UTF-8"), response.getContentType());
        Map<String, Object> envelope = mapper.readValue(
                new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(403, envelope.get("code"));
        assertEquals("无权限访问", envelope.get("message"));
        assertEquals(Boolean.FALSE, envelope.get("success"));
    }

    // ---------- helpers ----------

    @RequiresPermission("class:read")
    static class Ctl {
        @RequiresPermission("method:write")
        public void methodLevel() {
        }

        public void inheritsClassLevel() {
        }

        @RequiresPermission({})
        public void noPermission() {
        }

        @RequiresPermission(value = {"order:read", "order:export"}, logical = RequiresPermission.Logical.AND)
        public void bothRequired() {
        }

        @RequiresPermission({"order:read", "order:export"})
        public void eitherRequired() {
        }

        @RequiresPermission("system:user:list")
        public void systemUserList() {
        }

        @RequiresPermission("user:list")
        public void userList() {
        }

        @RequiresPermission("   ")
        public void blankPermission() {
        }
    }

    static class PlainController {
        public void bare() {
        }
    }

    private HandlerMethod handler(Class<?> controllerType, String methodName) {
        try {
            for (Method method : controllerType.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return new HandlerMethod(controllerType.getDeclaredConstructor().newInstance(), method);
                }
            }
            throw new IllegalStateException("no such test method: " + methodName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/system/user/list");
    }

    private MockHttpServletResponse utf8Response() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private void login(String username, Set<String> roles, Set<String> permissions) {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(1L).username(username).tenantId("T1").roles(roles).permissions(permissions).build());
    }

    private static Set<String> roles(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static Set<String> permissions(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private void assertForbidden(MockHttpServletResponse response, String expectedMessage) throws Exception {
        assertEquals(403, response.getStatus());
        Map<String, Object> envelope = mapper.readValue(
                new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(403, envelope.get("code"));
        assertEquals(expectedMessage, envelope.get("message"));
    }
}
