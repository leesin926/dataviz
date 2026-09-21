package com.dataviz.datasource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@MapperScan("com.dataviz.datasource.mapper")
@ComponentScan(basePackages = {"com.dataviz.datasource", "com.dataviz.common"})
public class DatasourceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatasourceServiceApplication.class, args);
    }
}
