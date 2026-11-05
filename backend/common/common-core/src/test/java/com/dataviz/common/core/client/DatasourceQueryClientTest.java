package com.dataviz.common.core.client;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.common.core.util.JsonUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内部只读取数客户端。三条判据最要命：
 * ①「取不到值」和「取数失败」必须分岔——空结果集返回 null，库故障/口令不一致必须抛 503，混起来就是漏报；
 * ②服务端错误码要原样透传，全塌成 503 会把"表不存在"伪装成"服务挂了"；
 * ③口令只走请求头，绝不进 URL、请求体或异常文案。
 * HTTP 层用桩 ClientHttpRequestFactory 垫在真的 RestTemplate 下面，所以断言的是真正发出去的字节。
 */
class DatasourceQueryClientTest {

    private static final String BASE_URL = "http://datasource:8083";
    private static final String PATH = "/api/datasource/internal/query";
    private static final String INTERNAL_TOKEN = "s3cr3t-internal-token";

    private final ObjectMapper objectMapper =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 最近一次发出去的请求，用于断言 URL / 头 / 请求体 */
    private MockClientHttpRequest lastRequest;

    // ---------- 成功路径 ----------

    @Test
    @DisplayName("标量查询命中首行首列：整数值成 BigDecimal")
    void should_returnBigDecimal_when_scalarRowPresent() {
        // Given
        DatasourceQueryClient client = clientReturning(200,
                envelope("{\"columns\":[\"cnt\"],\"rows\":[{\"cnt\":3}],\"rowCount\":1}"));

        // When
        BigDecimal value = client.queryScalar(7L, "select count(1) as cnt from t_order");

        // Then
        assertEquals(new BigDecimal("3"), value);
    }

    @Test
    @DisplayName("浮点/字符串数值都归一为 BigDecimal，字符串先裁首尾空白")
    void should_normaliseNumericTypes_when_firstColumnIsNumberLike() {
        // Given/When/Then
        assertEquals(new BigDecimal("12.5"), scalarOf("{\"amt\":12.5}"));
        assertEquals(new BigDecimal("42.75"), scalarOf("{\"amt\":\"42.75\"}"));
        assertEquals(new BigDecimal("7"), scalarOf("{\"amt\":\"  7  \"}"));
    }

    @Test
    @DisplayName("上游已给 BigDecimal 时原样透传，不做二次转换")
    void should_passThrough_when_valueAlreadyBigDecimal() {
        // Given
        DatasourceQueryResult result = new DatasourceQueryResult();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Collections.<String, Object>singletonMap("amt", new BigDecimal("99.990")));
        result.setRows(rows);
        DatasourceQueryClient client = clientWithMockedExchange(result);

        // When
        BigDecimal value = client.queryScalar(7L, "select amt from t");

        // Then
        assertEquals(new BigDecimal("99.990"), value);
    }

    @Test
    @DisplayName("首列不是数值 → 400 并点名实际类型，让调用方能改 SQL")
    void should_throwBadRequest_when_firstColumnNotNumeric() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"columns\":[\"v\"],\"rows\":[{\"v\":\"abc\"}]}"));

        // When
        BizException ex = assertThrows(BizException.class, () -> client.queryScalar(7L, "select v"));

        // Then
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("SQL 的首列不是数值，无法比较: String", ex.getMessage());
    }

    @Test
    @DisplayName("返回结构完整透传：行数据、rowCount、truncated 都交给上层")
    void should_returnWholeResult_when_querySucceeds() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope(
                "{\"columns\":[\"a\",\"b\"],\"rows\":[{\"a\":1,\"b\":2},{\"a\":3,\"b\":4}],\"rowCount\":2,\"truncated\":true}"));

        // When
        DatasourceQueryResult result = client.query(7L, "select a,b from t", 500, 20);

        // Then
        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getRows().size());
        assertEquals(Integer.valueOf(4), result.getRows().get(1).get("b"));
        assertTrue(result.isTruncated());
    }

    // ---------- 「取不到值」的几种空 ----------

    @Test
    @DisplayName("空结果集是「无法判定」返回 null，既不抛异常也不当 0")
    void should_returnNull_when_resultSetEmpty() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"columns\":[\"cnt\"],\"rows\":[],\"rowCount\":0}"));

        // When/Then
        assertNull(client.queryScalar(7L, "select count(1) as cnt from t where 1=0"));
    }

    @Test
    @DisplayName("有一行但首列值为 null → null（与零行区分开）")
    void should_returnNull_when_firstColumnValueNull() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"rows\":[{\"v\":null}]}"));

        // When/Then
        assertNull(client.queryScalar(7L, "select v"));
    }

    @Test
    @DisplayName("首行是空对象（零列）→ null")
    void should_returnNull_when_firstRowHasNoColumns() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"rows\":[{}]}"));

        // When/Then
        assertNull(client.queryScalar(7L, "select v"));
    }

    @Test
    @DisplayName("上游把 rows 写成 null 时客户端原样交回，queryScalar 直接裸 NPE（绕开「取数失败=503」的约定）")
    void should_throwBareNpe_when_rowsFieldIsNull() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"columns\":[],\"rows\":null,\"rowCount\":0}"));

        // When
        DatasourceQueryResult raw = client.query(7L, "select v");

        // Then：query() 把畸形响应原样透传，queryScalar() 却在解引用上炸成 NPE 而不是 BizException
        assertNull(raw.getRows());
        assertThrows(NullPointerException.class, () -> client.queryScalar(7L, "select v"));
    }

    // ---------- 请求形状与凭据去向 ----------

    @Test
    @DisplayName("口令只在请求头：URL 无查询串、请求体里没有口令")
    void should_sendSecretInHeaderOnly_when_queryPosted() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"rows\":[{\"c\":1}]}"));

        // When
        client.query(7L, "select 1 c");

        // Then
        assertNotNull(lastRequest);
        assertEquals(BASE_URL + PATH, lastRequest.getURI().toString());
        assertNull(lastRequest.getURI().getQuery());
        assertEquals(INTERNAL_TOKEN, lastRequest.getHeaders().getFirst("X-Internal-Token"));
        assertEquals(MediaType.APPLICATION_JSON, lastRequest.getHeaders().getContentType());
        String body = new String(lastRequest.getBodyAsBytes(), StandardCharsets.UTF_8);
        assertFalse(body.contains(INTERNAL_TOKEN), body);
        assertFalse(body.toLowerCase().contains("token"), body);
        assertEquals("POST", lastRequest.getMethodValue());
    }

    @Test
    @DisplayName("maxRows / timeout 只在正数时进请求体，键名按服务端契约")
    void should_omitNonPositiveLimits_when_optionalArgsNotUsable() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"rows\":[{\"c\":1}]}"));

        // When
        client.query(7L, "select 1 c", 1000, 30);
        Map<String, Object> withLimits = bodyOf();
        client.query(7L, "select 1 c", 0, -1);
        Map<String, Object> zeroLimits = bodyOf();
        client.query(7L, "select 1 c", null, null);
        Map<String, Object> noLimits = bodyOf();

        // Then
        assertEquals(Integer.valueOf(1000), withLimits.get("maxRows"));
        assertEquals(Integer.valueOf(30), withLimits.get("timeout"));
        assertFalse(zeroLimits.containsKey("maxRows"), String.valueOf(zeroLimits));
        assertFalse(zeroLimits.containsKey("timeout"), String.valueOf(zeroLimits));
        assertFalse(noLimits.containsKey("maxRows"), String.valueOf(noLimits));
        assertEquals(7L, ((Number) noLimits.get("datasourceId")).longValue());
        assertEquals("select 1 c", noLimits.get("sql"));
    }

    @Test
    @DisplayName("base-url 结尾斜杠被削掉，不产生双斜杠路径")
    void should_trimTrailingSlash_when_baseUrlEndsWithSlash() {
        // Given
        DatasourceQueryClient client = clientWithBase("http://datasource:8083/", new StubFactory(200,
                envelope("{\"rows\":[{\"c\":1}]}")));

        // When
        client.query(1L, "select 1 c");

        // Then
        assertEquals("http://datasource:8083/api/datasource/internal/query", lastRequest.getURI().toString());
    }

    // ---------- 失败路径 ----------

    @Test
    @DisplayName("200 信封但 code 非 0 → 服务端错误码原样抛出，不塌成 503")
    void should_keepServerErrorCode_when_envelopeFailedOn200() {
        // Given
        DatasourceQueryClient client = clientReturning(200,
                "{\"code\":2002,\"message\":\"数据源不存在\",\"data\":null,\"timestamp\":1}");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(99L, "select 1"));

        // Then
        assertEquals(2002, ex.getCode());
        assertEquals("数据源不存在", ex.getMessage());
    }

    @Test
    @DisplayName("200 且 data 为 null → 503「无返回数据」")
    void should_throwUnavailable_when_successEnvelopeHasNullData() {
        // Given
        DatasourceQueryClient client = clientReturning(200, "{\"code\":0,\"message\":\"操作成功\",\"data\":null}");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select 1"));

        // Then
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
        assertEquals("数据源查询失败：数据源查询无返回数据", ex.getMessage());
    }

    @Test
    @DisplayName("200 但响应体是 JSON 字面量 null → 503「空响应体」")
    void should_throwUnavailable_when_responseBodyIsNullLiteral() {
        // Given
        DatasourceQueryClient client = clientReturning(200, "null");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select 1"));

        // Then
        assertEquals("数据源查询失败：数据源查询返回空响应体", ex.getMessage());
    }

    @Test
    @DisplayName("非 200 带错误信封 → 还原原始错误码与文案（表不存在不能被伪装成服务挂了）")
    void should_preserveOriginalError_when_errorEnvelopeOn500() {
        // Given
        DatasourceQueryClient client = clientReturning(500,
                "{\"code\":3001,\"message\":\"SQL执行错误: Table 'db.t_missing' doesn't exist\"}");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select * from t_missing"));

        // Then
        assertEquals(3001, ex.getCode());
        assertTrue(ex.getMessage().contains("t_missing"), ex.getMessage());
    }

    @Test
    @DisplayName("403 一律折算成口令不一致，且不回显口令")
    void should_reportTokenMismatch_when_forbiddenByServer() {
        // Given
        DatasourceQueryClient client = clientReturning(403, "{\"code\":403,\"message\":\"Invalid internal token\"}");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select 1"));

        // Then
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
        assertEquals("数据源查询失败：内部口令不被数据源服务接受", ex.getMessage());
        assertFalse(ex.getMessage().contains(INTERNAL_TOKEN));
    }

    @Test
    @DisplayName("非 JSON 响应体（网关 HTML 502）→ 503 并带上状态码")
    void should_reportStatus_when_bodyIsNotEnvelope() {
        // Given
        DatasourceQueryClient client = clientReturning(502, "<html>Bad Gateway</html>");

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select 1"));

        // Then
        assertEquals("数据源查询失败：数据源服务返回异常状态 502", ex.getMessage());
    }

    @Test
    @DisplayName("错误响应体为空串 → 走状态码兜底文案")
    void should_reportStatus_when_errorBodyEmpty() {
        // Given
        DatasourceQueryClient client = clientReturning(500, "");

        // When/Then
        assertEquals("数据源查询失败：数据源服务返回异常状态 500",
                assertThrows(BizException.class, () -> client.query(7L, "select 1")).getMessage());
    }

    @Test
    @DisplayName("连接超时/服务不可达 → 503，与「服务侧拒绝」分开表述")
    void should_throwUnavailable_when_connectFails() {
        // Given
        DatasourceQueryClient client = clientWithBase(BASE_URL,
                new StubFactory(new ResourceAccessException("connect timed out")));

        // When
        BizException ex = assertThrows(BizException.class, () -> client.query(7L, "select 1"));

        // Then
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
        assertTrue(ex.getMessage().startsWith("数据源查询失败：无法访问数据源服务"), ex.getMessage());
        assertFalse(ex.getMessage().contains(INTERNAL_TOKEN));
    }

    @Test
    @DisplayName("两参 query 等价于不带限行的四参调用")
    void should_delegateToFullQuery_when_twoArgOverloadUsed() {
        // Given
        DatasourceQueryClient client = clientReturning(200, envelope("{\"rows\":[{\"c\":1}]}"));

        // When
        DatasourceQueryResult result = client.query(7L, "select 1 c");

        // Then
        assertEquals(1, result.getRows().size());
        Map<String, Object> body = bodyOf();
        assertEquals(2, body.size());
        assertEquals("select 1 c", body.get("sql"));
    }

    // ---------- 超时口径 ----------

    @Test
    @DisplayName("读超时配 0 或负数时回落到 35 秒默认值，连接超时固定 3 秒")
    void should_fallBackTo35Seconds_when_readTimeoutNotPositive() {
        // Given
        RestTemplateBuilder builder = mockBuilder();

        // When
        new DatasourceQueryClient(builder, objectMapper, BASE_URL, INTERNAL_TOKEN, 0);

        // Then
        verify(builder).setReadTimeout(Duration.ofSeconds(35));
        verify(builder).setConnectTimeout(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("读超时按配置生效")
    void should_applyConfiguredTimeout_when_readTimeoutPositive() {
        // Given
        RestTemplateBuilder builder = mockBuilder();

        // When
        new DatasourceQueryClient(builder, objectMapper, BASE_URL, INTERNAL_TOKEN, 12);

        // Then
        verify(builder).setReadTimeout(Duration.ofSeconds(12));
    }

    // ---------- helpers ----------

    private BigDecimal scalarOf(String rowJson) {
        DatasourceQueryClient client = clientReturning(200,
                envelope("{\"columns\":[\"c\"],\"rows\":[" + rowJson + "],\"rowCount\":1}"));
        return client.queryScalar(7L, "select c");
    }

    private Map<String, Object> bodyOf() {
        String raw = new String(lastRequest.getBodyAsBytes(), StandardCharsets.UTF_8);
        return JsonUtils.fromJson(raw, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private String envelope(String dataJson) {
        return "{\"code\":0,\"message\":\"操作成功\",\"data\":" + dataJson + ",\"timestamp\":1}";
    }

    private DatasourceQueryClient clientReturning(int status, String body) {
        return clientWithBase(BASE_URL, new StubFactory(status, body));
    }

    private DatasourceQueryClient clientWithBase(String baseUrl, StubFactory factory) {
        return new DatasourceQueryClient(new RestTemplateBuilder().requestFactory(() -> factory),
                objectMapper, baseUrl, INTERNAL_TOKEN, 35);
    }

    /** 只在「上游已给 BigDecimal」这类无法由 JSON 表达的对象图时使用 */
    private DatasourceQueryClient clientWithMockedExchange(DatasourceQueryResult result) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        R<DatasourceQueryResult> ok =
                new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), result);
        given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                ArgumentMatchers.<ParameterizedTypeReference<R<DatasourceQueryResult>>>any()))
                .willReturn(new ResponseEntity<R<DatasourceQueryResult>>(ok, HttpStatus.OK));
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);
        return new DatasourceQueryClient(builder, objectMapper, BASE_URL, INTERNAL_TOKEN, 5);
    }

    private RestTemplateBuilder mockBuilder() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(new RestTemplate());
        return builder;
    }

    /**
     * 桩工厂：继承 SimpleClientHttpRequestFactory 只是为了让 RestTemplateBuilder 的
     * setConnectTimeout/setReadTimeout 找得到 setter（裸匿名工厂在 build() 阶段就抛 IllegalStateException）。
     */
    private final class StubFactory extends SimpleClientHttpRequestFactory {
        private final int status;
        private final String body;
        private final RuntimeException failure;

        private StubFactory(int status, String body) {
            this.status = status;
            this.body = body;
            this.failure = null;
        }

        private StubFactory(RuntimeException failure) {
            this.status = 0;
            this.body = null;
            this.failure = failure;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            if (failure != null) {
                throw failure;
            }
            MockClientHttpRequest request = new MockClientHttpRequest(httpMethod, uri);
            MockClientHttpResponse response = new MockClientHttpResponse(
                    body.getBytes(StandardCharsets.UTF_8), HttpStatus.resolve(status));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.setResponse(response);
            lastRequest = request;
            return request;
        }
    }
}
