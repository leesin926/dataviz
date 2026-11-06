package com.dataviz.datasource.connector;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 数据源 config（JSON 字符串列，见 API-106）的连接器侧解析视图。
 * <p>
 * 统一从注入的 ObjectMapper 读 {@code host/port/database/username/password/useTls/apiKey/scheme}，
 * 把用户输入的两类坑固定成具体 400 而不是 NPE：
 * <ul>
 *   <li>缺失/空白的 {@code host} → 400 并点名 {@code host} 这个 key（API-124 同类问题）；</li>
 *   <li>{@code scheme}（或 {@code protocol}）只允许 http/https —— config 是用户提供的，
 *       SSRF 面必须守住：file/ftp/jdbc/gopher 之类一律 400 拒绝。</li>
 * </ul>
 * 凭据（username/password/apiKey）保留在本对象里仅供构造请求头使用；任何日志、URL、
 * 异常文案都不得拼接它们 —— 各连接器的日志只打 host:port/database。
 */
@Getter
public final class ConnectorConfig {

    private final String typeLabel;
    private final String scheme;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useTls;
    private final String apiKey;

    private ConnectorConfig(String typeLabel, String scheme, String host, int port, String database,
                            String username, String password, boolean useTls, String apiKey) {
        this.typeLabel = typeLabel;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useTls = useTls;
        this.apiKey = apiKey;
    }

    /**
     * @param defaultPort 该连接器协议的缺省端口（CK 8123 / ES 9200 / Mongo 27017）
     * @param typeLabel   错误文案里的类型名（"ClickHouse" 等）
     */
    public static ConnectorConfig parse(ObjectMapper mapper, String configJson, int defaultPort, String typeLabel) {
        JsonNode config;
        try {
            if (configJson == null || configJson.trim().isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        typeLabel + " 数据源 config 为空：需要 JSON 字符串（host/port/database 等）");
            }
            config = mapper.readTree(configJson);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    typeLabel + " 数据源 config 不是合法 JSON 字符串，无法解析: " + e.getMessage());
        }
        if (config == null || !config.isObject()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    typeLabel + " 数据源 config 必须是 JSON 对象（host/port/database 等）");
        }

        String host = text(config.get("host"));
        if (!StringUtils.hasText(host)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    typeLabel + " 数据源 config 缺少 host：主机地址（host）不能为空");
        }

        JsonNode portNode = config.get("port");
        int port = defaultPort;
        if (portNode != null && !portNode.isNull()) {
            port = portNode.asInt(defaultPort);
            if (port <= 0 || port > 65535) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        typeLabel + " 数据源 config 的 port 不合法（1-65535）: " + portNode.asText());
            }
        }

        boolean useTls = config.path("useTls").asBoolean(false);
        String scheme = explicitScheme(config);
        if (scheme == null) {
            scheme = useTls ? "https" : "http";
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            // 用户可控的 scheme：只放行 http/https，其它一律拒绝（SSRF 面收缩）
            throw new BizException(ErrorCode.BAD_REQUEST,
                    typeLabel + " 数据源 config 的 scheme/protocol 仅支持 http/https，当前为: " + scheme);
        }

        return new ConnectorConfig(typeLabel, scheme, host.trim(), port,
                textOrDefault(config.get("database"), "default"),
                text(config.get("username")), text(config.get("password")),
                useTls, text(config.get("apiKey")));
    }

    private static String explicitScheme(JsonNode config) {
        String raw = text(config.get("scheme"));
        if (raw == null) {
            raw = text(config.get("protocol"));
        }
        return raw == null ? null : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 不含任何凭据的基础地址：scheme://host:port。 */
    public String baseUrl() {
        return scheme + "://" + host + ":" + port;
    }

    /** 日志专用：host:port，绝不带凭据。 */
    public String hostPort() {
        return host + ":" + port;
    }

    public boolean hasBasicAuth() {
        return StringUtils.hasText(username);
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    /** Basic 认证头值；凭据只进请求头，不进 URL。 */
    public String basicAuthHeader() {
        String raw = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        return "Basic " + new String(Base64.getEncoder().encode(raw.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.US_ASCII);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String textOrDefault(JsonNode node, String fallback) {
        String value = text(node);
        return StringUtils.hasText(value) ? value : fallback;
    }
}
