package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Elasticsearch HTTP 连接器（8.12.0 实测口径：GET / 带 version.number；POST /_sql 出 columns/rows；
 * GET /_cat/indices?format=json 列表）。
 * <p>
 * 核心判据：ES 的 _sql 只吃 ES SQL 语法子集，<b>不是任意 SQL</b> —— 一旦 ES 报错，
 * 异常里必须是它自己的 error.reason 原文（塌成"SQL执行失败"用户就不知道该改哪段语法）；
 * 以及凭据只在请求头、URL 与异常文案不带口令。
 */
class ElasticsearchConnectorTest {

    private static final String PASSWORD = "es-sup3r-pass";
    private static final String CONFIG = "{\"host\":\"es.internal\",\"port\":9200,"
            + "\"username\":\"elastic\",\"password\":\"" + PASSWORD + "\"}";
    private static final String NO_AUTH_CONFIG = "{\"host\":\"es.internal\",\"port\":9200}";

    /** 8.12.0 POST /_sql {"query":"SELECT 1 AS one"} 的录制响应形状。 */
    private static final String SQL_FIXTURE =
            "{\"columns\":[{\"name\":\"one\",\"type\":\"integer\"},{\"name\":\"cnt\",\"type\":\"long\"}],"
                    + "\"rows\":[[1,10],[2,20]]}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ConnectorRestClientFactory restClients = mock(ConnectorRestClientFactory.class);
    private ElasticsearchConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ElasticsearchConnector(objectMapper, restClients);
        lenient().when(restClients.create(anyInt())).thenReturn(restTemplate);
    }

    // ---- helpers -----------------------------------------------------------

    private void stub(HttpMethod method, String body) {
        when(restTemplate.exchange(anyString(), eq(method), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private String capturedUrl(HttpMethod method) {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(url.capture(), eq(method), any(HttpEntity.class), eq(String.class));
        return url.getValue();
    }

    private HttpEntity<?> capturedEntity(HttpMethod method) {
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(method), entity.capture(), eq(String.class));
        return entity.getValue();
    }

    // =======================================================================

    @Test
    @DisplayName("supports - 只认领 ELASTICSEARCH，能力位与 ClickHouse 同为数据访问档")
    void should_supportOnlyElasticsearch_when_typeChecked() {
        assertThat(connector.supports(DatasourceType.ELASTICSEARCH)).isTrue();
        assertThat(connector.supports(DatasourceType.CLICKHOUSE)).isFalse();
        assertThat(connector.supports(DatasourceType.MYSQL)).isFalse();
        assertThat(connector.capability().isQuery()).isTrue();
        assertThat(connector.capability().isListTables()).isTrue();
    }

    @Test
    @DisplayName("查询 - _sql 录制的 columns/rows 映射为既有响应形状，值保持 JSON 原生类型")
    void should_mapColumnsAndRows_when_sqlSucceeds() {
        // Given
        stub(HttpMethod.POST, SQL_FIXTURE);

        // When
        DatasourceQueryResult result = connector.query(NO_AUTH_CONFIG, "SELECT one, count(*) AS cnt FROM logs",
                1000, 30);

        // Then
        assertThat(result.getColumns()).containsExactly("one", "cnt");
        assertThat(result.getRows().get(0).get("one")).isEqualTo(1);
        assertThat(result.getRows().get(1).get("cnt")).isEqualTo(20);
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getSql()).isEqualTo("SELECT one, count(*) AS cnt FROM logs");

        // 请求体是 {"query": …} JSON，而非裸 SQL
        String body = (String) capturedEntity(HttpMethod.POST).getBody();
        assertThat(body).isEqualTo("{\"query\":\"SELECT one, count(*) AS cnt FROM logs\"}");
        assertThat(capturedUrl(HttpMethod.POST)).isEqualTo("http://es.internal:9200/_sql");
    }

    @Test
    @DisplayName("查询 - ES 语法错误必须原文上浮 error.reason，而不是泛化「SQL执行失败」")
    void should_surfaceElasticsearchReasonVerbatim_when_sqlRejectedWith400() throws Exception {
        // Given: ES 错误体按真实结构程序化生成（reason 含引号与换行，正是"原文上浮"最容易丢的形状）
        String reason = "Encountered \"\\\"\" at line 1, column 1.\nWas expecting one of:\n    <EOF> \n";
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "parsing_exception");
        error.put("reason", reason);
        error.put("lang", "expression");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("status", 400);
        String esErrorBody = objectMapper.writeValueAsString(body);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientResponseException(
                        "400 Bad Request on POST for http://es.internal:9200/_sql",
                        400, "Bad Request", null, esErrorBody.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        // When / Then
        assertThatThrownBy(() -> connector.query(NO_AUTH_CONFIG, "SELECT \" FROM logs", 1000, 30))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SQL_EXECUTION_ERROR.getCode())
                .hasMessageContaining(reason);
    }

    @Test
    @DisplayName("查询 - 200 但体内带 error 的形态同样上浮 reason，不解析成空结果")
    void should_surfaceEmbeddedError_when_httpOkButPayloadSaysNo() {
        // Given
        stub(HttpMethod.POST, "{\"error\":{\"type\":\"illegal_argument_exception\","
                + "\"reason\":\"Unknown index [nope]\"},\"status\":400}");

        // When / Then
        assertThatThrownBy(() -> connector.query(NO_AUTH_CONFIG, "SELECT 1 FROM nope", 1000, 30))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Unknown index [nope]");
    }

    @Test
    @DisplayName("测连接 - GET / 读到 version.number 才算通过")
    void should_pass_when_rootReportsVersionNumber() {
        // Given
        stub(HttpMethod.GET, "{\"name\":\"node-1\",\"cluster_name\":\"docker-es\","
                + "\"version\":{\"number\":\"8.12.0\",\"lucene_version\":\"9.9.0\"},\"tagline\":\"You Know, for Search\"}");

        // When / Then
        assertThat(connector.testConnection(NO_AUTH_CONFIG)).isTrue();
        assertThat(capturedUrl(HttpMethod.GET)).isEqualTo("http://es.internal:9200/");
    }

    @Test
    @DisplayName("测连接 - 对端不是 ES（响应缺 version.number）时报具体原因，不谎报连接正常")
    void should_throw_when_rootResponseMissingVersionNumber() {
        // Given: 端口撞车 —— 回了 JSON 但没有 version.number
        stub(HttpMethod.GET, "{\"status\":\"green\"}");

        // When / Then
        assertThatThrownBy(() -> connector.testConnection(NO_AUTH_CONFIG))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.DATASOURCE_CONNECTION_FAILED.getCode())
                .hasMessageContaining("version.number");
    }

    @Test
    @DisplayName("列表 - _cat/indices?format=json 过滤掉 . 前缀系统索引并按名排序")
    void should_filterSystemIndices_when_listTables() {
        // Given
        stub(HttpMethod.GET, "[{\"health\":\"green\",\"status\":\"open\",\"index\":\"metrics\",\"docs.count\":\"3\"},"
                + "{\"health\":\"green\",\"status\":\"open\",\"index\":\".kibana_1\",\"docs.count\":\"5\"},"
                + "{\"health\":\"yellow\",\"status\":\"open\",\"index\":\"logs-2024.11\",\"docs.count\":\"9\"}]");

        // When
        List<String> indices = connector.listTables(NO_AUTH_CONFIG);

        // Then
        assertThat(indices).containsExactly("logs-2024.11", "metrics");
        assertThat(capturedUrl(HttpMethod.GET)).isEqualTo("http://es.internal:9200/_cat/indices?format=json");
    }

    @Test
    @DisplayName("凭据 - username/password 只进 Basic 头，URL 与请求体都不带口令")
    void should_keepBasicCredentialsOutOfUrl_when_usernameConfigured() {
        // Given
        stub(HttpMethod.POST, SQL_FIXTURE);

        // When
        connector.query(CONFIG, "SELECT one FROM t", 1000, 30);

        // Then
        String expectedBasic = "Basic " + new String(
                Base64.getEncoder().encode(("elastic:" + PASSWORD).getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.US_ASCII);
        assertThat(capturedEntity(HttpMethod.POST).getHeaders().getFirst("Authorization")).isEqualTo(expectedBasic);
        assertThat(capturedUrl(HttpMethod.POST)).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("凭据 - apiKey 走 ApiKey 请求头，同样绝不进 URL")
    void should_useApiKeyHeader_when_apiKeyConfigured() {
        // Given
        stub(HttpMethod.POST, SQL_FIXTURE);

        // When
        connector.query("{\"host\":\"es.internal\",\"apiKey\":\"my-key-123\"}", "SELECT one FROM t", 1000, 30);

        // Then
        assertThat(capturedEntity(HttpMethod.POST).getHeaders().getFirst("Authorization"))
                .isEqualTo("ApiKey my-key-123");
        assertThat(capturedUrl(HttpMethod.POST)).doesNotContain("my-key-123");
    }

    @Test
    @DisplayName("凭据 - security off（无凭据配置）时不发 Authorization 头")
    void should_sendNoAuthHeader_when_noCredentialsConfigured() {
        // Given
        stub(HttpMethod.POST, SQL_FIXTURE);

        // When
        connector.query(NO_AUTH_CONFIG, "SELECT one FROM t", 1000, 30);

        // Then
        assertThat(capturedEntity(HttpMethod.POST).getHeaders().getFirst("Authorization")).isNull();
    }

    @Test
    @DisplayName("配置 - scheme 非 http/https 与缺 host 都是点名具体 key 的 400，一次 HTTP 都不发")
    void should_rejectBadSchemeAndBlankHost_when_configInvalid() {
        assertThatThrownBy(() -> connector.query("{\"host\":\"es\",\"scheme\":\"gopher\"}", "SELECT 1", 1000, 30))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("仅支持 http/https")
                .hasMessageContaining("gopher");

        assertThatThrownBy(() -> connector.listTables("{\"port\":9200}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少 host");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("查询 - timeoutSeconds 传给读超时客户端；超出 maxRows 的行被截断并置 truncated")
    void should_passTimeoutAndTruncate_when_limitsGiven() {
        // Given
        stub(HttpMethod.POST, "{\"columns\":[{\"name\":\"x\",\"type\":\"integer\"}],"
                + "\"rows\":[[1],[2],[3]]}");

        // When
        DatasourceQueryResult result = connector.query(NO_AUTH_CONFIG, "SELECT x FROM big", 2, 9);

        // Then
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.isTruncated()).isTrue();
        verify(restClients).create(9);
    }
}
