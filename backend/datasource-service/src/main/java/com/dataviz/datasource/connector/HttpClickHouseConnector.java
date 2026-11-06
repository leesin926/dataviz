package com.dataviz.datasource.connector;

import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ClickHouse over HTTP（8123 端口原生接口，不依赖 clickhouse-jdbc jar —— 离线仓库里它只有 pom 没有 jar）。
 * <p>
 * 协议口径（对 23.12 实测）：
 * <ul>
 *   <li>{@code POST {base}/?database=x}，请求体 {@code SELECT … FORMAT JSONCompact}，
 *       响应 {@code {"meta":[{"name":…,"type":…}],"data":[[…]],"rows":n}}；</li>
 *   <li>凭据走 {@code Authorization: Basic} 请求头，绝不拼进 URL —— 本仓库规约：凭据只进头，
 *       不进 URL/日志/请求体；</li>
 *   <li>{@code timeoutSeconds} 同时落成 URL 参数 {@code max_execution_time}（秒，服务端侧）
 *       与 RestTemplate 读超时（传输侧），两头都夹住。注意 ClickHouse 没有
 *       {@code query_max_execution_time} 这个 setting（23.12 实测：UNKNOWN_SETTING，建议名就是
 *       {@code max_execution_time}），写错的话每一条查询都会被引擎拒掉；</li>
 *   <li>列表走 {@code system.tables}（database=currentDatabase()），连通性用 {@code SELECT 1}
 *       而不是 {@code /ping} —— ping 对错误口令也回 Ok.，用它测连接会把"凭据不对"谎报成"连接正常"。</li>
 * </ul>
 * <p>
 * 数值映射策略（有意为之，单测锁定）：ClickHouse 的 JSONCompact 为避免 JS 精度丢失，把 UInt64/Int64
 * 等宽整数渲染成字符串（如 {@code "0"}）。本连接器<b>只在 meta[].type 声明为 UInt/Int/Float/Decimal
 * 时</b>才把"看起来是数字"的单元格转成数字：Int/UInt→Long（溢出退 BigDecimal，覆盖 UInt64&gt;Long.MAX），
 * Float/Decimal→BigDecimal（保留标度，Decimal(18,4) 的 3.1400 不会掉尾零）；解析失败即原样保留字符串，
 * 不猜。其它类型（String/Date/DateTime/Bool…）不做数字猜测；Array/Map 等复合类型原样透传 JsonNode，
 * 由 Jackson 序列化为 JSON 结构。这样告警阈值比较、{@code queryScalar} 拿数值等下游语义与 JDBC 路径一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpClickHouseConnector implements DatasourceConnector {

    static final int DEFAULT_PORT = 8123;
    private static final int TEST_TIMEOUT_SECONDS = 5;
    private static final int LIST_TABLES_MAX_ROWS = 10_000;
    private static final int LIST_TABLES_TIMEOUT_SECONDS = 10;
    private static final String LIST_TABLES_SQL =
            "SELECT name FROM system.tables WHERE database = currentDatabase() ORDER BY name";

    private final ObjectMapper objectMapper;
    private final ConnectorRestClientFactory restClients;

    @Override
    public boolean supports(DatasourceType type) {
        return type == DatasourceType.CLICKHOUSE;
    }

    @Override
    public ConnectorCapability capability() {
        return ConnectorCapability.dataAccess();
    }

    @Override
    public boolean testConnection(String configJson) {
        ConnectorConfig config = parse(configJson);
        try {
            // SELECT 1 同时验证网络可达与 Basic 口令有效（/ping 不校验凭据，见类注释）
            executeQuery(config, "SELECT 1 AS ok", TEST_TIMEOUT_SECONDS);
            log.info("ClickHouse 连接测试通过: {} database={}", config.hostPort(), config.getDatabase());
            return true;
        } catch (RestClientResponseException e) {
            throw connectFailed(config, e);
        } catch (ResourceAccessException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "ClickHouse HTTP 连接失败: " + config.hostPort() + " - " + e.getMessage());
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "ClickHouse HTTP 连接失败: " + config.hostPort() + " - " + e.getMessage());
        }
    }

    @Override
    public List<String> listTables(String configJson) {
        JsonNode root = queryTranslated(parse(configJson), LIST_TABLES_SQL,
                LIST_TABLES_MAX_ROWS, LIST_TABLES_TIMEOUT_SECONDS, "获取表列表");
        List<String> tables = new ArrayList<>();
        for (JsonNode row : rowsOf(root)) {
            if (row.isArray() && row.size() > 0 && !row.get(0).isNull()) {
                tables.add(row.get(0).asText());
            }
        }
        return tables;
    }

    @Override
    public DatasourceQueryResult query(String configJson, String sql, int maxRows, int timeoutSeconds) {
        ConnectorConfig config = parse(configJson);
        long startTime = System.currentTimeMillis();
        JsonNode root = queryTranslated(config, sql, maxRows, timeoutSeconds, "查询");

        List<String> columns = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (JsonNode meta : root.path("meta")) {
            columns.add(meta.path("name").asText());
            types.add(meta.path("type").asText(""));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        int index = 0;
        for (JsonNode dataRow : rowsOf(root)) {
            if (index >= maxRows) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int col = 0; col < columns.size(); col++) {
                JsonNode cell = dataRow.isArray() && col < dataRow.size() ? dataRow.get(col) : null;
                row.put(columns.get(col), convertCell(cell, types.get(col)));
            }
            rows.add(row);
            index++;
        }

        DatasourceQueryResult result = new DatasourceQueryResult();
        result.setColumns(columns);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(truncated);
        result.setExecutionTime(System.currentTimeMillis() - startTime);
        result.setSql(sql);
        return result;
    }

    // ---- internals ---------------------------------------------------------

    private ConnectorConfig parse(String configJson) {
        return ConnectorConfig.parse(objectMapper, configJson, DEFAULT_PORT, "ClickHouse");
    }

    /**
     * ClickHouse 侧的失败一律翻成带引擎原文的 BizException。
     * <p>实跑发现的欠账：listTables 把 404 的 RestClientException 原样抛给全局异常处理器，
     * 调用方只看到"系统内部错误"，而引擎其实写明了原因（UNKNOWN_SETTING 那一条）。
     * 查询与列表共用这一套翻译，免得改了一条漏了另一条。</p>
     */
    private JsonNode queryTranslated(ConnectorConfig config, String sql, int maxRows, int timeoutSeconds, String what) {
        try {
            return queryInternal(config, sql, maxRows, timeoutSeconds);
        } catch (RestClientResponseException e) {
            // ClickHouse 的错误体（Code: NN. DB::Exception: …）就是最有用的原因，原样带出；
            // 响应体里没有凭据，异常消息也不拼 username/password。
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "ClickHouse " + what + "失败: " + brief(e));
        } catch (ResourceAccessException e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "查询超时: " + e.getMessage());
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "ClickHouse " + what + "失败: " + e.getMessage());
        }
    }

    private JsonNode queryInternal(ConnectorConfig config, String sql, int maxRows, int timeoutSeconds) {
        String body = ensureJsonCompact(sql);
        // maxRows 是客户端截断（ClickHouse 的 max_result_rows 会直接报错而非截断，语义对不上 JDBC 路径）
        String url = config.baseUrl() + "/?database=" + urlEncode(config.getDatabase())
                + (timeoutSeconds > 0 ? "&max_execution_time=" + timeoutSeconds : "");
        return executeQuery(config, body, url, timeoutSeconds);
    }

    private JsonNode executeQuery(ConnectorConfig config, String sql, int timeoutSeconds) {
        String url = config.baseUrl() + "/?database=" + urlEncode(config.getDatabase());
        return executeQuery(config, ensureJsonCompact(sql), url, timeoutSeconds);
    }

    private JsonNode executeQuery(ConnectorConfig config, String body, String url, int timeoutSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        if (config.hasBasicAuth()) {
            headers.set(HttpHeaders.AUTHORIZATION, config.basicAuthHeader());
        }
        RestTemplate restTemplate = restClients.create(timeoutSeconds);
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR,
                    "ClickHouse 响应不是合法 JSON: " + briefOfText(response.getBody()));
        }
    }

    /** SQL 未显式指定 FORMAT 时追加 JSONCompact（数组行格式，meta 带类型），去掉尾分号避免语法噪声。 */
    private static String ensureJsonCompact(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.toUpperCase(Locale.ROOT).contains("FORMAT ")) {
            return trimmed;
        }
        return trimmed + " FORMAT JSONCompact";
    }

    private JsonNode rowsOf(JsonNode root) {
        return root.path("data");
    }

    /**
     * 按 meta[].type 决定是否把字符串数字还原成数字，规则见类 javadoc。
     * Nullable(LowCardinality(X)) 之类先逐层剥壳再判定。
     */
    private Object convertCell(JsonNode cell, String chType) {
        if (cell == null || cell.isNull() || cell.isMissingNode()) {
            return null;
        }
        String base = unwrapType(chType);
        if (cell.isNumber() || cell.isTextual()) {
            String text = cell.asText().trim();
            if (isIntegerType(base)) {
                try {
                    return Long.valueOf(text);
                } catch (NumberFormatException ignored) {
                    try {
                        return new BigDecimal(text);
                    } catch (NumberFormatException stillNotNumeric) {
                        return cell.isTextual() ? cell.asText() : cell;
                    }
                }
            }
            if (isDecimalType(base)) {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException ignored) {
                    return cell.isTextual() ? cell.asText() : cell;
                }
            }
        }
        if (base.startsWith("Bool") || cell.isBoolean()) {
            return cell.asBoolean();
        }
        // String/Date/DateTime/Array/…：不做数字猜测；复合节点原样透传供 Jackson 序列化
        return cell.isTextual() ? cell.asText() : cell;
    }

    private static String unwrapType(String chType) {
        String current = chType == null ? "" : chType.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String wrapper : new String[]{"Nullable", "LowCardinality"}) {
                String prefix = wrapper + "(";
                if (current.startsWith(prefix) && current.endsWith(")")) {
                    current = current.substring(prefix.length(), current.length() - 1).trim();
                    changed = true;
                }
            }
        }
        return current;
    }

    private static boolean isIntegerType(String base) {
        return base.startsWith("UInt") || base.startsWith("Int");
    }

    private static boolean isDecimalType(String base) {
        return base.startsWith("Float") || base.startsWith("Decimal");
    }

    private BizException connectFailed(ConnectorConfig config, RestClientResponseException e) {
        // 只带状态码与 ClickHouse 错误行；不打印请求头，凭据不会出现在这里
        throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                "ClickHouse 连接测试失败: " + config.hostPort() + " database=" + config.getDatabase()
                        + " - " + brief(e));
    }

    private static String brief(RestClientResponseException e) {
        return briefOfText(e.getResponseBodyAsString());
    }

    private static String briefOfText(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "响应体为空";
        }
        String firstLine = raw.trim().split("\n", 2)[0];
        return firstLine.length() > 500 ? firstLine.substring(0, 500) : firstLine;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
