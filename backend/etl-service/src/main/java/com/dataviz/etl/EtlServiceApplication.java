package com.dataviz.etl;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ETL服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
@MapperScan("com.dataviz.etl.mapper")
@ComponentScan(basePackages = {"com.dataviz.etl", "com.dataviz.common"})
public class EtlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlServiceApplication.class, args);
    }
}
