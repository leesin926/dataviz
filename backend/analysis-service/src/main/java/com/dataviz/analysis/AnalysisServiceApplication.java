package com.dataviz.analysis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 分析服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.dataviz.analysis.mapper")
@ComponentScan(basePackages = {"com.dataviz.analysis", "com.dataviz.common"})
public class AnalysisServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysisServiceApplication.class, args);
    }
}
