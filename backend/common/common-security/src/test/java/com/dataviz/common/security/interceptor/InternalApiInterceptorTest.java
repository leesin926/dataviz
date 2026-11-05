package com.dataviz.common.security.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内部接口闸门。核心口径是「口令没配就一律拒绝」——不做"没配就放开"的降级，
 * 因为意外公开一个能读口令哈希的端点比登录暂时失败严重得多。
 * 另一条是比对必须是全等（常量时间比较），前缀/包含关系都不能放行。
 */
class InternalApiInterceptorTest {

    private static final String EXPECTED = "internal-shared-secret-2024";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private InternalApiInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new InternalApiInterceptor();
    }

    @Test
    @DisplayName("非 Controller 方法（静态资源）不进闸门")
    void should_passThrough_when_handlerIsNotHandlerMethod() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest(EXPECTED), response, new Object());

        // Then
        assertTrue(passed);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    @DisplayName("口令未配置（空串）→ 403「Internal API is not configured」，带上正确口令也拒")
    void should_denyAll_when_tokenNotConfigured() throws Exception {
        // Given
        configure("");
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest(EXPECTED), response, handler());

        // Then
        assertFalse(passed);
        assertEquals(403, response.getStatus());
        assertEnvelope(response, 403, "Internal API is not configured");
    }

    @Test
    @DisplayName("口令只配了空白字符也按未配置处理")
    void should_denyAll_when_tokenIsWhitespaceOnly() throws Exception {
        // Given
        configure("   ");
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest("   "), response, handler());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 403, "Internal API is not configured");
    }

    @Test
    @DisplayName("未带头 → 403「Invalid internal token」")
    void should_deny_when_headerMissing() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/internal/sync");
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(request, response, handler());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 403, "Invalid internal token");
    }

    @Test
    @DisplayName("错口令 → 403，且不回显期望口令")
    void should_deny_when_headerWrong() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest("wrong-token"), response, handler());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 403, "Invalid internal token");
        assertFalse(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).contains(EXPECTED));
    }

    @Test
    @DisplayName("口令是期望值的前缀也不能放行：比对是全等不是 startsWith")
    void should_deny_when_headerIsPrefixOfExpected() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest(EXPECTED.substring(0, 10)), response, handler());

        // Then
        assertFalse(passed);
        assertEnvelope(response, 403, "Invalid internal token");
    }

    @Test
    @DisplayName("口令大小写不同即拒绝")
    void should_deny_when_headerDiffersByCase() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When/Then
        assertFalse(interceptor.preHandle(internalRequest(EXPECTED.toUpperCase()), response, handler()));
        assertEnvelope(response, 403, "Invalid internal token");
    }

    @Test
    @DisplayName("正确口令放行，且不写响应体")
    void should_pass_when_headerMatches() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When
        boolean passed = interceptor.preHandle(internalRequest(EXPECTED), response, handler());

        // Then
        assertTrue(passed);
        assertEquals(200, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
        assertNull(response.getContentType());
    }

    @Test
    @DisplayName("未配置口令的告警只打一次，但每一次请求都仍然被拒")
    void should_keepDenying_when_configWarningAlreadyEmitted() throws Exception {
        // Given
        configure("");
        assertFalse((Boolean) ReflectionTestUtils.getField(interceptor, "configWarned"));

        // When
        interceptor.preHandle(internalRequest(null), utf8Response(), handler());

        // Then
        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(interceptor, "configWarned"));
        MockHttpServletResponse second = utf8Response();
        assertFalse(interceptor.preHandle(internalRequest(EXPECTED), second, handler()));
        assertEnvelope(second, 403, "Internal API is not configured");
    }

    @Test
    @DisplayName("响应是 application/json;charset=UTF-8 的统一信封")
    void should_writeJsonEnvelope_when_denied() throws Exception {
        // Given
        configure(EXPECTED);
        MockHttpServletResponse response = utf8Response();

        // When
        interceptor.preHandle(internalRequest("nope"), response, handler());

        // Then
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        Map<String, Object> envelope = mapper.readValue(
                new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(403, envelope.get("code"));
        assertEquals(Boolean.FALSE, envelope.get("success"));
        assertNull(envelope.get("data"));
    }

    // ---------- helpers ----------

    private void configure(String token) {
        ReflectionTestUtils.setField(interceptor, "expectedToken", token);
    }

    private MockHttpServletRequest internalRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/internal/sync");
        request.setRemoteAddr("10.0.0.5");
        if (token != null) {
            request.addHeader(InternalApiInterceptor.TOKEN_HEADER, token);
        }
        return request;
    }

    private MockHttpServletResponse utf8Response() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private HandlerMethod handler() {
        try {
            Method method = InternalEndpoint.class.getDeclaredMethod("sync");
            return new HandlerMethod(new InternalEndpoint(), method);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertEnvelope(MockHttpServletResponse response, int expectedCode, String expectedMessage)
            throws Exception {
        Map<String, Object> envelope = mapper.readValue(
                new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() { });
        assertEquals(expectedCode, envelope.get("code"));
        assertEquals(expectedMessage, envelope.get("message"));
    }

    static class InternalEndpoint {
        public void sync() {
        }
    }
}
