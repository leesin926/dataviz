package com.dataviz.datasource.connector;

import org.springframework.web.client.RestTemplate;

/**
 * 按读超时（秒）取 RestTemplate 的接缝。
 * <p>
 * RestTemplate 的超时是"每个实例一个"而非"每个请求一个"，而查询通道的 timeoutSeconds 由调用方逐次
 * 传入；同时 HTTP 连接器要能在单测里脱离容器运行 —— 所以连接器不直接持有 RestTemplate，
 * 而是依赖本接口，测试里 mock 出被捕获调用的 RestTemplate 即可。
 * （超时口径自建、不注入各服务已有的 restTemplate bean：同 {@code DatasourceQueryClient} 的理由，
 * 共用 bean 会让"谁注入了哪个"变成隐式契约。）
 */
public interface ConnectorRestClientFactory {

    RestTemplate create(int readTimeoutSeconds);
}
