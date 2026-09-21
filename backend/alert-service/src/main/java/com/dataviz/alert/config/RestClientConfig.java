package com.dataviz.alert.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * 通知发送是另一个量级的风险：外部 webhook 一挂就会把整轮规则检查堵住，
     * 所以给它一个短超时实例。指标取数用的长超时实例在 common-core 的
     * {@code DatasourceQueryClient} 内部自建（见 D45），这里不再重复声明。
     */
    @Bean("notifyRestTemplate")
    public RestTemplate notifyRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
