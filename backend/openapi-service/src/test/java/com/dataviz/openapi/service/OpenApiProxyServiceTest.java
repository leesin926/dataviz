package com.dataviz.openapi.service;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.openapi.config.OpenApiProxyProperties;
import com.dataviz.openapi.dto.ApiProxyRequestDTO;
import com.dataviz.openapi.dto.ProxyForwardResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 代理转发判据的分支覆盖。核心不是"能不能转出去"，而是四件事：
 * 白名单没命中必须点名拒绝（服务代码没配 / 路径没配 / 该服务压根没有内部端点），
 * 请求体里的任意 URL 永远换不成服务端要敲的地址（SSRF），
 * 上游状态与响应体如实透传（200 是真 200，5xx 是真 5xx，绝不塌成"转发生成功"），
 * 口令没配置时 fail-closed，不拿空口令去换上游 403。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiProxyServiceTest {

    private static final String TOKEN = "unit-test-internal-token";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    private OpenApiProxyProperties properties;
    private OpenApiProxyService service;

    @BeforeEach
    void setUp() {
        properties = new OpenApiProxyProperties();

        OpenApiProxyProperties.Target model = new OpenApiProxyProperties.Target();
        model.setBaseUrl("http://localhost:8085");
        model.setPaths(Arrays.asList("/api/model/internal/dataset/{id}", "/api/model/internal/dataset/{id}/fields"));
        properties.getServices().put("model", model);

        OpenApiProxyProperties.Target datasource = new OpenApiProxyProperties.Target();
        datasource.setBaseUrl("http://localhost:8083");
        datasource.setPaths(Collections.singletonList("/api/datasource/internal/query"));
        properties.getServices().put("datasource", datasource);

        OpenApiProxyProperties.Target user = new OpenApiProxyProperties.Target();
        user.setBaseUrl("http://localhost:8082");
        user.setStripApiPrefix(true);
        user.setPaths(Collections.singletonList("/api/user/internal/auth-user"));
        properties.getServices().put("user", user);

        // screen 演示"服务存在但没暴露内部端点"：paths 为空，代理必须点名拒绝而不是打一条注定 401 的请求
        OpenApiProxyProperties.Target screen = new OpenApiProxyProperties.Target();
        screen.setBaseUrl("http://localhost:8088");
        properties.getServices().put("screen", screen);

        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        service = newServiceWithToken(TOKEN);
    }

    private OpenApiProxyService newServiceWithToken(String token) {
        return new OpenApiProxyService(properties, restTemplateBuilder, new ObjectMapper(), token);
    }

    private ApiProxyRequestDTO request(String apiPath, String method) {
        ApiProxyRequestDTO dto = new ApiProxyRequestDTO();
        dto.setAppKey("ak-1");
        dto.setAppSecret("sk-1");
        dto.setApiPath(apiPath);
        dto.setMethod(method);
        return dto;
    }

    // ---------- 白名单拒绝 ----------

    @Test
    @DisplayName("服务代码没配进白名单 → 拒绝，消息点名是哪个服务、已配置的是哪些")
    void should_refuseWithNamedServiceCode_when_serviceNotAllowlisted() {
        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("/api/analysis/dashboard/list", "GET")));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("analysis"), e.getMessage());
        assertTrue(e.getMessage().contains("model"), e.getMessage());
    }

    @Test
    @DisplayName("服务在白名单、路径不在 → 拒绝，消息带上原路径与白名单内容")
    void should_refuseWithNamedPath_when_pathNotAllowlisted() {
        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("/api/model/internal/dataset/7/recompute", "GET")));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("/api/model/internal/dataset/7/recompute"), e.getMessage());
        assertTrue(e.getMessage().contains("openapi.proxy.services.model.paths"), e.getMessage());
    }

    @Test
    @DisplayName("服务没暴露任何内部只读端点 → 点名拒绝，绝不打一条注定被拒的路径")
    void should_refuseWithReason_when_serviceHasNoInternalEndpoints() {
        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("/api/screen/internal/published", "GET")));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("未暴露任何内部只读端点"), e.getMessage());
    }

    @Test
    @DisplayName("apiPath 给绝对 URL（SSRF 尝试）→ 拒绝，只认 /api/<服务代码>/ 形状")
    void should_refuseAbsoluteUrl_when_apiPathIsFullUrl() {
        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("http://169.254.169.254/latest/meta-data", "GET")));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("必须以 /api/"), e.getMessage());

        BizException blank = assertThrows(BizException.class,
                () -> service.forward(request("   ", "GET")));
        assertEquals(400, blank.getCode());
        assertTrue(blank.getMessage().contains("apiPath 不能为空"), blank.getMessage());
    }

    @Test
    @DisplayName("PUT/DELETE 等改动类动词一律拒绝，哪怕路径在白名单里")
    void should_refuse_when_methodIsNotReadOnly() {
        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("/api/model/internal/dataset/7", "DELETE")));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("DELETE"), e.getMessage());
    }

    @Test
    @DisplayName("internal.api.token 没配置 → fail-closed 拒绝，不拿空口令去换上游 403")
    void should_refuse_when_internalTokenMissing() {
        OpenApiProxyService noTokenService = newServiceWithToken("  ");

        BizException e = assertThrows(BizException.class,
                () -> noTokenService.forward(request("/api/model/internal/dataset/7", "GET")));

        assertEquals(503, e.getCode());
        assertTrue(e.getMessage().contains("internal.api.token"), e.getMessage());
    }

    // ---------- 真实转发 ----------

    @Test
    @DisplayName("上游 200：真实状态与真实响应体原样带出，GET 参数编码进查询串、口令进请求头")
    void should_surfaceRealBodyAndStatus_when_upstreamReturns200() {
        String upstreamJson = "{\"code\":0,\"message\":\"操作成功\",\"data\":{\"id\":7,\"name\":\"销售数据集\"}}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(jsonResponse(HttpStatus.OK, upstreamJson));

        ApiProxyRequestDTO dto = request("/api/model/internal/dataset/7", "GET");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("page", 1);
        params.put("keyword", "销售");
        dto.setParams(params);

        ProxyForwardResult result = service.forward(dto);

        assertEquals(200, result.getStatus());
        assertTrue(result.is2xxSuccess());
        // 响应体是解析后的结构，不是"转发生成功"的假 map
        Map<?, ?> body = (Map<?, ?>) result.getBody();
        assertEquals(0, body.get("code"));
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("销售数据集", data.get("name"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET),
                entityCaptor.capture(), eq(String.class));
        assertTrue(urlCaptor.getValue().startsWith("http://localhost:8085/api/model/internal/dataset/7?"),
                urlCaptor.getValue());
        // 中文参数必须 URL 编码：销售 -> %E9%94%80%E5%94%AE
        assertTrue(urlCaptor.getValue().contains("keyword=%E9%94%80%E5%94%AE"), urlCaptor.getValue());
        assertTrue(urlCaptor.getValue().contains("page=1"), urlCaptor.getValue());
        HttpHeaders sent = entityCaptor.getValue().getHeaders();
        assertEquals(TOKEN, sent.getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("白名单内的 POST（datasource 内部只读查询）：params 序列化成 JSON 体发出")
    void should_postJsonBody_when_allowedQueryPathIsPost() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(jsonResponse(HttpStatus.OK, "{\"code\":0,\"data\":{\"rows\":[]}}"));

        ApiProxyRequestDTO dto = request("/api/datasource/internal/query", "POST");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("datasourceId", 3);
        params.put("sql", "SELECT 1");
        dto.setParams(params);

        ProxyForwardResult result = service.forward(dto);

        assertEquals(200, result.getStatus());
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:8083/api/datasource/internal/query"),
                eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        String sentBody = String.valueOf(entityCaptor.getValue().getBody());
        assertTrue(sentBody.contains("\"datasourceId\":3"), sentBody);
        assertTrue(sentBody.contains("\"sql\":\"SELECT 1\""), sentBody);
    }

    @Test
    @DisplayName("网关剥前缀的服务（user）直连时要换成正真映射路径 /user/internal/...")
    void should_stripApiPrefix_when_targetRequiresIt() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(jsonResponse(HttpStatus.OK, "{\"code\":0,\"data\":null}"));

        service.forward(request("/api/user/internal/auth-user", "GET"));

        verify(restTemplate).exchange(eq("http://localhost:8082/user/internal/auth-user"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("上游 5xx：状态与响应体如实带出，不美化成成功")
    void should_propagateUpstreamStatus_when_upstreamReturns503() {
        String errorBody = "{\"code\":503,\"message\":\"model-service 过载\"}";
        HttpServerErrorException upstream = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                new HttpHeaders(), errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(upstream);

        ProxyForwardResult result = service.forward(request("/api/model/internal/dataset/7", "GET"));

        assertEquals(503, result.getStatus());
        assertFalse(result.is2xxSuccess());
        Map<?, ?> body = (Map<?, ?>) result.getBody();
        assertEquals("model-service 过载", body.get("message"));
    }

    @Test
    @DisplayName("连不上上游：报 503 服务不可用，消息点名是哪个服务打的地址")
    void should_failServiceUnavailable_when_connectionRefused() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        BizException e = assertThrows(BizException.class,
                () -> service.forward(request("/api/model/internal/dataset/7", "GET")));

        assertEquals(503, e.getCode());
        assertTrue(e.getMessage().contains("model"), e.getMessage());
        assertTrue(e.getMessage().contains("http://localhost:8085/api/model/internal/dataset/7"), e.getMessage());
    }

    @Test
    @DisplayName("上游返回非 JSON（网关 HTML 错误页之类）：原样字符串带出，不吞不编")
    void should_keepRawBody_when_upstreamBodyIsNotJson() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(
                        "<html>502 Bad Gateway</html>", HttpStatus.BAD_GATEWAY));

        ProxyForwardResult result = service.forward(request("/api/model/internal/dataset/7", "GET"));

        assertEquals(502, result.getStatus());
        assertEquals("<html>502 Bad Gateway</html>", result.getBody());
    }

    // ---------- helpers ----------

    private static ResponseEntity<String> jsonResponse(HttpStatus status, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<String>(body, headers, status);
    }
}
