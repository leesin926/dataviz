package com.dataviz.datasource.connector;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生产实现：RestTemplateBuilder + 固定 3 秒连接超时 + 按读超时缓存复用，避免每次查询重建连接池。
 */
@Component
@RequiredArgsConstructor
public class BuilderBackedConnectorRestClientFactory implements ConnectorRestClientFactory {

    private static final int CONNECT_TIMEOUT_SECONDS = 3;

    private final RestTemplateBuilder builder;
    private final Map<Integer, RestTemplate> cache = new ConcurrentHashMap<>();

    @Override
    public RestTemplate create(int readTimeoutSeconds) {
        int seconds = readTimeoutSeconds > 0 ? readTimeoutSeconds : 30;
        return cache.computeIfAbsent(seconds, timeout -> builder
                .setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .setReadTimeout(Duration.ofSeconds(timeout))
                .build());
    }
}
