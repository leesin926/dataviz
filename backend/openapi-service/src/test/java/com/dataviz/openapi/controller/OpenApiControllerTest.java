package com.dataviz.openapi.controller;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.R;
import com.dataviz.openapi.config.OpenApiProxyProperties;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.dto.ProxyForwardResult;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.service.ApiAuthService;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.service.OpenApiProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 控制器层的两件事：审计行与响应码必须诚实（上游状态原样进 openapi_log.response_code 与 R.code，
 * 拒绝也要落一行），以及客户端地址的取法（默认只信 TCP 对端 —— 这是写进 openapi_log.ip 审计字段的值，
 * XFF 最左值是客户端自写的；只有显式配了可信跳数才从右往左数）。
 * 转发判据本身见 {@code OpenApiProxyServiceTest}，这里把代理 mock 掉，只考控制器自己的分支。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiControllerTest {

    @Mock
    private ApiAuthService apiAuthService;

    @Mock
    private ApiLogService apiLogService;

    @Mock
    private OpenApiProxyService openApiProxyService;

    private OpenApiProxyProperties proxyProperties;
    private OpenApiController controller;

    @BeforeEach
    void setUp() {
        proxyProperties = new OpenApiProxyProperties();
        controller = new OpenApiController(apiAuthService, apiLogService, openApiProxyService, proxyProperties);
    }

    private OpenApiClient activeClient() {
        return OpenApiClient.builder()
                .id(9L).appName("测试接入方").appKey("ak-9").appSecret("sk-9").status("ACTIVE").build();
    }

    private ApiProxyRequestDTO dto() {
        ApiProxyRequestDTO dto = new ApiProxyRequestDTO();
        dto.setAppKey("ak-9");
        dto.setAppSecret("sk-9");
        dto.setApiPath("/api/model/internal/dataset/7");
        dto.setMethod("GET");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("page", 1);
        dto.setParams(params);
        return dto;
    }

    private MockHttpServletRequest servletRequest(String remoteAddr, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/openapi/proxy/invoke");
        request.setRemoteAddr(remoteAddr);
        if (xff != null) {
            request.addHeader("X-Forwarded-For", xff);
        }
        return request;
    }

    private OpenApiLog capturedLog() {
        ArgumentCaptor<OpenApiLog> captor = ArgumentCaptor.forClass(OpenApiLog.class);
        verify(apiLogService).saveLog(captor.capture());
        return captor.getValue();
    }

    // ---------- 客户端地址 ----------

    @Test
    @DisplayName("默认 trusted-proxy-hops=0：XFF 写什么都没用，审计 ip 取不可伪造的 TCP 对端")
    void should_useSocketPeer_when_trustedProxyHopsZero() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenReturn(new ProxyForwardResult(200, "ok"));

        controller.proxy(dto(), servletRequest("10.0.0.9", "6.6.6.6, 10.0.0.1"));

        // 鉴权与审计两处都用同一个口径
        verify(apiAuthService).authenticate("ak-9", "sk-9", "10.0.0.9");
        assertEquals("10.0.0.9", capturedLog().getIp());
    }

    @Test
    @DisplayName("配置 trusted-proxy-hops=1：取 XFF 从右往左第 1 项（可信代理追加的对端），最左伪造值被跳过")
    void should_takeConfiguredHop_when_trustedProxyHopsOne() {
        proxyProperties.setTrustedProxyHops(1);
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenReturn(new ProxyForwardResult(200, "ok"));

        controller.proxy(dto(), servletRequest("10.0.0.9", "6.6.6.6, 10.0.0.1"));

        // 客户端自写的 6.6.6.6 不进审计；可信代理追加在最右的 10.0.0.1 才是客户端
        assertEquals("10.0.0.1", capturedLog().getIp());
    }

    @Test
    @DisplayName("声明了跳数但 XFF 比代理链短（头没带上/被剥掉）→ 退回 TCP 对端，宁保守不轻信")
    void should_fallBackToSocketPeer_when_forwardedHeaderShorterThanHops() {
        proxyProperties.setTrustedProxyHops(2);
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenReturn(new ProxyForwardResult(200, "ok"));

        controller.proxy(dto(), servletRequest("10.0.0.9", "6.6.6.6"));

        assertEquals("10.0.0.9", capturedLog().getIp());
    }

    // ---------- 响应与审计的诚实性 ----------

    @Test
    @DisplayName("上游 200：R.data 就是上游真实响应体，审计行状态码记 200 且字段齐")
    void should_returnRealBody_when_upstreamSucceeds() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        Map<String, Object> upstreamBody = new HashMap<String, Object>();
        upstreamBody.put("code", 0);
        upstreamBody.put("data", "数据集元数据");
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenReturn(new ProxyForwardResult(200, upstreamBody));

        R<Object> response = controller.proxy(dto(), servletRequest("10.0.0.9", null));

        assertEquals(0, response.getCode());
        assertSame(upstreamBody, response.getData());

        OpenApiLog log = capturedLog();
        assertEquals(Long.valueOf(9L), log.getClientId());
        assertEquals("测试接入方", log.getAppName());
        assertEquals("/api/model/internal/dataset/7", log.getApiPath());
        assertEquals("GET", log.getMethod());
        assertEquals(Integer.valueOf(200), log.getResponseCode());
        assertNotNull(log.getExecutionTime());
        assertNotNull(log.getCreateTime());
    }

    @Test
    @DisplayName("上游 5xx：R.code 记真实上游状态、消息带原因，绝不合成成功；审计行同样记 5xx")
    void should_propagateUpstreamFailure_when_upstreamReturns500() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        Map<String, Object> errorBody = new HashMap<String, Object>();
        errorBody.put("message", "model-service 内部炸了");
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenReturn(new ProxyForwardResult(500, errorBody));

        R<Object> response = controller.proxy(dto(), servletRequest("10.0.0.9", null));

        assertEquals(500, response.getCode());
        assertTrue(response.getMessage().contains("model-service 内部炸了"), response.getMessage());
        assertEquals(Integer.valueOf(500), capturedLog().getResponseCode());
    }

    @Test
    @DisplayName("白名单拒绝（BizException 403）：原因原样回给调用方，且同样落一行审计")
    void should_logRefusalWithReason_when_allowlistRejects() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenThrow(new BizException(403, "路径 [/api/model/internal/dataset/7/delete] 不在服务 [model] 的可调用白名单内"));

        R<Object> response = controller.proxy(dto(), servletRequest("10.0.0.9", null));

        assertEquals(403, response.getCode());
        assertTrue(response.getMessage().contains("不在服务 [model] 的可调用白名单内"), response.getMessage());
        OpenApiLog log = capturedLog();
        assertEquals(Integer.valueOf(403), log.getResponseCode());
        assertEquals("10.0.0.9", log.getIp());
    }

    @Test
    @DisplayName("转发抛非预期异常：记 500 并留痕，不静默吞掉")
    void should_record500_when_unexpectedForwardException() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(activeClient());
        when(openApiProxyService.forward(any(ApiProxyRequestDTO.class)))
                .thenThrow(new IllegalStateException("boom"));

        R<Object> response = controller.proxy(dto(), servletRequest("10.0.0.9", null));

        assertEquals(500, response.getCode());
        assertTrue(response.getMessage().contains("boom"), response.getMessage());
        assertEquals(Integer.valueOf(500), capturedLog().getResponseCode());
    }

    @Test
    @DisplayName("鉴权不过也入一条拒绝账（API-129：原先 401 绕过审计段，日志页永远只显示成功）")
    void should_recordRefusal_when_authenticationFails() {
        // Given：AppKey/Secret 校验未通过（外部最容易触发的失败）
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(com.dataviz.common.core.result.ErrorCode.UNAUTHORIZED, "AppKey 或密钥不正确"));

        BizException thrown = assertThrows(BizException.class,
                () -> controller.proxy(dto(), servletRequest("10.0.0.9", null)));

        // Then：错误照原样抛出，但审计段确实进了一行
        assertTrue(thrown.getMessage().contains("AppKey"), thrown.getMessage());
        OpenApiLog refusal = capturedLog();
        assertEquals(Integer.valueOf(401), refusal.getResponseCode());
        assertEquals("/api/model/internal/dataset/7", refusal.getApiPath());
        assertEquals("10.0.0.9", refusal.getIp());
    }

    @Test
    @DisplayName("拒绝账不认领归属、也不抄请求体：没验过的钥匙不能写 clientId，失败体里可能带半把口令")
    void should_notClaimClientOrParams_when_refusalLogged() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(com.dataviz.common.core.result.ErrorCode.FORBIDDEN, "来源 IP 不在白名单"));

        assertThrows(BizException.class, () -> controller.proxy(dto(), servletRequest("10.0.0.9", null)));

        OpenApiLog refusal = capturedLog();
        assertNull(refusal.getClientId(), "归属未经校验，写入等于让外部自称");
        assertNull(refusal.getAppName());
        assertNull(refusal.getRequestParams(), "拒绝的调用不得把请求体抄进日志");
        assertEquals(Integer.valueOf(403), refusal.getResponseCode());
    }

    @Test
    @DisplayName("审计写入失败只降级为 warn，不改变对外的 401")
    void should_stillReturn401_when_auditWriteFails() {
        when(apiAuthService.authenticate(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(com.dataviz.common.core.result.ErrorCode.UNAUTHORIZED, "AppKey 或密钥不正确"));
        org.mockito.Mockito.doThrow(new IllegalStateException("库不可达")).when(apiLogService).saveLog(any(OpenApiLog.class));

        BizException thrown = assertThrows(BizException.class,
                () -> controller.proxy(dto(), servletRequest("10.0.0.9", null)));

        assertEquals(401, thrown.getCode());
        verify(apiLogService).saveLog(any(OpenApiLog.class));
    }
}
