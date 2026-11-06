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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
 * ClickHouse HTTP 连接器：用 ClickHouse 23.12 实测过的 JSONCompact 录制报文喂桩 RestTemplate，
 * 断言的是真正发出去的 URL/请求头/请求体与解析出来的值类型。
 * <p>
 * 三条判据：①凭据只在 Authorization 头里 —— URL、异常文案都不许出现口令；
 * ②数字转换只信 meta[].type —— UInt/Int/Float/Decimal 才转，String 列里的 "123" 绝不变成数字；
 * ③scheme 与 host 属于用户输入 —— 非 http/https、缺 host 必须落成点名 key 的 400，而不是 NPE/500。
 */
class HttpClickHouseConnectorTest {

    private static final String PASSWORD = "clickhouse123";
    private static final String CONFIG = "{\"host\":\"ch.internal\",\"port\":8123,\"database\":\"dataviz\","
            + "\"username\":\"default\",\"password\":\"" + PASSWORD + "\"}";

    /** ClickHouse 23.12 实测录制的 JSONCompact：UInt64 等宽整数是字符串（"0"），null 走 JSON null。 */
    private static final String JSON_COMPACT_FIXTURE =
            "{\"meta\":[{\"name\":\"n\",\"type\":\"UInt64\"},"
                    + "{\"name\":\"label\",\"type\":\"String\"},"
                    + "{\"name\":\"ratio\",\"type\":\"Nullable(Float64)\"},"
                    + "{\"name\":\"amt\",\"type\":\"Decimal(18, 4)\"}],"
                    + "\"data\":[[\"12\",\"a\",null,\"3.1400\"],[\"13\",\"b\",\"1.5\",\"0.5000\"]],"
                    + "\"rows\":2,\"rows_before_limit_at_least\":2,"
                    + "\"statistics\":{\"read_rows\":2,\"read_bytes\":64,\"elapsed_ms\":1}}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ConnectorRestClientFactory restClients = mock(ConnectorRestClientFactory.class);
    private HttpClickHouseConnector connector;

    @BeforeEach
    void setUp() {
        connector = new HttpClickHouseConnector(objectMapper, restClients);
        lenient().when(restClients.create(anyInt())).thenReturn(restTemplate);
    }

    // ---- helpers -----------------------------------------------------------

    private void stubPostResponse(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private String capturedUrl() {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        return url.getValue();
    }

    private HttpEntity<?> capturedEntity() {
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entity.capture(), eq(String.class));
        return entity.getValue();
    }

    // =======================================================================

    @Test
    @DisplayName("supports - 只认领 CLICKHOUSE，且声明数据访问能力")
    void should_supportOnlyClickHouse_when_typeChecked() {
        assertThat(connector.supports(DatasourceType.CLICKHOUSE)).isTrue();
        assertThat(connector.supports(DatasourceType.MYSQL)).isFalse();
        assertThat(connector.supports(DatasourceType.ELASTICSEARCH)).isFalse();
        assertThat(connector.supports(DatasourceType.MONGODB)).isFalse();
        assertThat(connector.capability().isTestConnection()).isTrue();
        assertThat(connector.capability().isListTables()).isTrue();
        assertThat(connector.capability().isQuery()).isTrue();
        // 连接器接口不提供列元数据与写入：能力位必须说实话
        assertThat(connector.capability().isListColumns()).isFalse();
        assertThat(connector.capability().isBatchWrite()).isFalse();
    }

    @Test
    @DisplayName("查询 - 录制 JSONCompact 报文按 meta 列名出列，数字列按 type 还原且 Decimal 保标度")
    void should_mapColumnsAndTypedValues_when_queryReturnsJsonCompact() {
        // Given
        stubPostResponse(JSON_COMPACT_FIXTURE);

        // When
        DatasourceQueryResult result = connector.query(CONFIG, "SELECT n, label, ratio, amt FROM t", 1000, 30);

        // Then
        assertThat(result.getColumns()).containsExactly("n", "label", "ratio", "amt");
        assertThat(result.getRows()).hasSize(2);
        Map<String, Object> first = result.getRows().get(0);
        // UInt64 的 "12" 按 type 转成数字（与 JDBC 路径同为数值，下游告警阈值比较才不塌）
        assertThat(first.get("n")).isEqualTo(12L);
        assertThat(first.get("label")).isEqualTo("a");
        assertThat(first.get("ratio")).isNull();
        // Decimal(18,4) 用 BigDecimal 保留标度：3.1400 不许掉尾零变成 3.14
        assertThat((BigDecimal) first.get("amt")).isEqualByComparingTo("3.14");
        assertThat(((BigDecimal) first.get("amt")).scale()).isEqualTo(4);
        assertThat(result.getRows().get(1).get("ratio")).isEqualTo(new BigDecimal("1.5"));
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getSql()).isEqualTo("SELECT n, label, ratio, amt FROM t");
        assertThat(result.getExecutionTime()).isGreaterThanOrEqualTo(0L);

        // 发出的请求体必须带 FORMAT JSONCompact；URL 是 database 参数、不含凭据
        String body = (String) capturedEntity().getBody();
        assertThat(body).isEqualTo("SELECT n, label, ratio, amt FROM t FORMAT JSONCompact");
        assertThat(capturedUrl()).startsWith("http://ch.internal:8123/?database=dataviz")
                .contains("max_execution_time=30");
    }

    @Test
    @DisplayName("查询 - 数字转换只信 type：String 列的 \"123\" 保持字符串，Int64 列的 \"N/A\" 不硬猜")
    void should_notGuessNumbers_when_typeDeclaredStringOrCellNotNumeric() {
        // Given
        stubPostResponse("{\"meta\":[{\"name\":\"code\",\"type\":\"String\"},"
                + "{\"name\":\"broken\",\"type\":\"Int64\"}],"
                + "\"data\":[[\"123\",\"N/A\"]],\"rows\":1}");

        // When
        DatasourceQueryResult result = connector.query(CONFIG, "SELECT code, broken FROM t", 1000, 30);

        // Then
        assertThat(result.getRows().get(0).get("code")).isEqualTo("123");
        assertThat(result.getRows().get(0).get("broken")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("查询 - UInt64 超出 Long 范围时退 BigDecimal，不溢出也不留字符串")
    void should_widenToBigDecimal_when_unsignedExceedsLong() {
        // Given
        stubPostResponse("{\"meta\":[{\"name\":\"big\",\"type\":\"UInt64\"}],\"data\":[[\"18446744073709551615\"]],"
                + "\"rows\":1}");

        // When
        DatasourceQueryResult result = connector.query(CONFIG, "SELECT big FROM t", 1000, 30);

        // Then
        assertThat(result.getRows().get(0).get("big")).isEqualTo(new BigDecimal("18446744073709551615"));
    }

    @Test
    @DisplayName("查询 - 凭据只进 Authorization 请求头：URL 与请求体中绝不出现口令")
    void should_keepCredentialsInHeaderOnly_when_basicAuthConfigured() {
        // Given
        stubPostResponse("{\"meta\":[{\"name\":\"ok\",\"type\":\"UInt8\"}],\"data\":[[\"1\"]],\"rows\":1}");

        // When
        connector.query(CONFIG, "SELECT ok FROM t", 1000, 30);

        // Then
        String expectedBasic = "Basic " + new String(
                Base64.getEncoder().encode(("default:" + PASSWORD).getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.US_ASCII);
        assertThat(capturedEntity().getHeaders().getFirst("Authorization")).isEqualTo(expectedBasic);
        assertThat(capturedUrl()).doesNotContain(PASSWORD).doesNotContain("default:");
        assertThat((String) capturedEntity().getBody()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("查询 - scheme 非 http/https（SSRF 面守卫）直接 400，一次 HTTP 都不发")
    void should_rejectNonHttpScheme_when_configSchemeIsFile() {
        // Given
        String evil = "{\"host\":\"ch.internal\",\"scheme\":\"file\",\"database\":\"dataviz\"}";

        // When / Then
        assertThatThrownBy(() -> connector.query(evil, "SELECT 1", 1000, 30))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.BAD_REQUEST.getCode())
                .hasMessageContaining("仅支持 http/https")
                .hasMessageContaining("file");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("配置 - host 缺失/空白 → 点名 host 的 400，而不是 NPE（API-124 同类）")
    void should_throwBadRequestNamingHostKey_when_configHostMissing() {
        assertThatThrownBy(() -> connector.query("{\"port\":8123}", "SELECT 1", 1000, 30))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少 host");

        assertThatThrownBy(() -> connector.testConnection("{\"host\":\"  \",\"port\":8123}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("host");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("查询 - timeoutSeconds 同时落成 max_execution_time 与客户端读超时；maxRows 截断置 truncated")
    void should_passTimeoutAndTruncateRows_when_limitsGiven() {
        // Given: 服务端回了 3 行，但 maxRows=2
        stubPostResponse("{\"meta\":[{\"name\":\"id\",\"type\":\"UInt64\"}],"
                + "\"data\":[[\"1\"],[\"2\"],[\"3\"]],\"rows\":3}");

        // When
        DatasourceQueryResult result = connector.query(CONFIG, "SELECT id FROM t", 2, 7);

        // Then
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRowCount()).isEqualTo(2);
        assertThat(result.isTruncated()).isTrue();
        // 参数名钉死成 max_execution_time：ClickHouse 23.12 实跑对 query_max_execution_time 回
        // "Code: 115 UNKNOWN_SETTING（Maybe you meant ['max_execution_time']）"，写错名每条查询都失败。
        assertThat(capturedUrl()).contains("max_execution_time=7").doesNotContain("query_max_execution_time");
        verify(restClients).create(7);
    }

    @Test
    @DisplayName("列表 - 走 system.tables（currentDatabase），名称按序返回")
    void should_listTablesFromSystemTables_when_queryReturnsNames() {
        // Given
        stubPostResponse("{\"meta\":[{\"name\":\"name\",\"type\":\"String\"}],"
                + "\"data\":[[\"orders\"],[\"users\"]],\"rows\":2}");

        // When
        List<String> tables = connector.listTables(CONFIG);

        // Then
        assertThat(tables).containsExactly("orders", "users");
        String body = (String) capturedEntity().getBody();
        assertThat(body).contains("FROM system.tables").contains("currentDatabase()")
                .contains("FORMAT JSONCompact");
    }

    @Test
    @DisplayName("测连接 - 用 SELECT 1 而不是 /ping：口令无效也必须失败，不能谎报连接正常")
    void should_runSelectOne_when_testConnection() {
        // Given
        stubPostResponse("{\"meta\":[{\"name\":\"ok\",\"type\":\"UInt8\"}],\"data\":[[\"1\"]],\"rows\":1}");

        // When
        boolean ok = connector.testConnection(CONFIG);

        // Then
        assertThat(ok).isTrue();
        String body = (String) capturedEntity().getBody();
        assertThat(body).startsWith("SELECT 1").contains("FORMAT JSONCompact");
    }

    @Test
    @DisplayName("测连接 - 401 时把 ClickHouse 错误原文带出，但异常文案里绝不出现口令")
    void should_surfaceClickHouseErrorWithoutPassword_when_testConnectionUnauthorized() {
        // Given
        String ckError = "Code: 194. DB::Exception: default: Authentication failed: password is incorrect";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientResponseException("401 Unauthorized for http://ch.internal:8123/",
                        401, "Unauthorized", null, ckError.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        // When / Then
        assertThatThrownBy(() -> connector.testConnection(CONFIG))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.DATASOURCE_CONNECTION_FAILED.getCode())
                .hasMessageContaining("Authentication failed")
                .hasMessageNotContaining(PASSWORD);
    }

    /**
     * 实跑发现的欠账：listTables 遇到 ClickHouse 的 4xx 时把 RestClientException 原样抛出，
     * 全局异常处理器只能回"系统内部错误"，引擎写明的原因（那一条 UNKNOWN_SETTING）全丢了。
     * 现在两条路都必须带原文，且都不许漏出口令。
     */
    @Test
    @DisplayName("失败翻译 - 列表与查询的 4xx 都带出引擎原文，不再让调用方只看到系统内部错误")
    void should_translateClickHouseErrorIntoBizException_when_listTablesOrQueryRejected() {
        String ckError = "Code: 115. DB::Exception: Setting query_max_execution_time is neither a builtin "
                + "setting nor ... Maybe you meant ['max_execution_time']. (UNKNOWN_SETTING)";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientResponseException("404 Not Found for http://ch.internal:8123/",
                        404, "Not Found", null, ckError.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        assertThatThrownBy(() -> connector.listTables(CONFIG))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SQL_EXECUTION_ERROR.getCode())
                .hasMessageContaining("获取表列表失败")
                .hasMessageContaining("UNKNOWN_SETTING")
                .hasMessageNotContaining(PASSWORD);

        assertThatThrownBy(() -> connector.query(CONFIG, "SELECT 1", 100, 30))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ClickHouse 查询失败")
                .hasMessageContaining("UNKNOWN_SETTING")
                .hasMessageNotContaining(PASSWORD);
    }
}
