package com.dataviz.datasource.factory;

import com.dataviz.datasource.entity.DatasourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源连接池工厂
 * 根据数据源配置JSON创建和管理HikariCP连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceConnectionFactory {

    private final Map<Long, HikariDataSource> poolMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 从连接池获取连接
     */
    public Connection getConnection(Long datasourceId, String type, String configJson) {
        HikariDataSource ds = poolMap.computeIfAbsent(datasourceId,
                id -> createDataSource(type, configJson));
        try {
            return ds.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection from pool: " + e.getMessage(), e);
        }
    }

    /**
     * 获取测试连接(不进入连接池)
     */
    public Connection getTestConnection(String type, String configJson) {
        try {
            String jdbcUrl = buildJdbcUrl(type, configJson);
            JsonNode config = objectMapper.readTree(configJson);
            String username = config.has("username") ? config.get("username").asText() : null;
            String password = config.has("password") ? config.get("password").asText() : null;
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test connection: " + e.getMessage(), e);
        }
    }

    /**
     * 移除并关闭连接池
     */
    public void removePool(Long datasourceId) {
        HikariDataSource ds = poolMap.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed connection pool for datasource: {}", datasourceId);
        }
    }

    @PreDestroy
    public void destroy() {
        poolMap.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
            }
        });
        poolMap.clear();
    }

    private HikariDataSource createDataSource(String type, String configJson) {
        try {
            JsonNode config = objectMapper.readTree(configJson);
            DatasourceType dsType = DatasourceType.fromCode(type);
            String jdbcUrl = buildJdbcUrl(type, configJson);

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            if (config.has("username")) {
                hikariConfig.setUsername(config.get("username").asText());
            }
            if (config.has("password")) {
                hikariConfig.setPassword(config.get("password").asText());
            }
            hikariConfig.setMaximumPoolSize(config.has("maxPoolSize") ? config.get("maxPoolSize").asInt() : 10);
            hikariConfig.setMinimumIdle(config.has("minIdle") ? config.get("minIdle").asInt() : 2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("dataviz-pool-" + dsType.getCode());

            log.info("Creating connection pool: {} -> {}", hikariConfig.getPoolName(), jdbcUrl);
            return new HikariDataSource(hikariConfig);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create data source: " + e.getMessage(), e);
        }
    }

    private String buildJdbcUrl(String type, String configJson) {
        try {
            JsonNode config = objectMapper.readTree(configJson);
            String host = config.has("host") ? config.get("host").asText() : "localhost";
            int port = config.has("port") ? config.get("port").asInt() : 3306;
            String database = config.has("database") ? config.get("database").asText() : "";

            DatasourceType dsType = DatasourceType.fromCode(type);
            String jdbcUrl;
            switch (dsType) {
                case MYSQL:
                    jdbcUrl = String.format(
                            "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                            host, port, database);
                    break;
                case POSTGRESQL:
                    jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
                    break;
                case CLICKHOUSE:
                    jdbcUrl = String.format("jdbc:clickhouse://%s:%d/%s", host, port, database);
                    break;
                case ORACLE:
                    jdbcUrl = String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);
                    break;
                case SQLSERVER:
                    jdbcUrl = String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, database);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported JDBC datasource type: " + type);
            }
            return jdbcUrl;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build JDBC URL: " + e.getMessage(), e);
        }
    }
}
