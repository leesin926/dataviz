package com.dataviz.model;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据模型服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.dataviz.model.mapper")
@ComponentScan(basePackages = {"com.dataviz.model", "com.dataviz.common"})
public class ModelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelServiceApplication.class, args);
    }
}
