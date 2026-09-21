package com.dataviz.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * User Management Service Application
 * <p>
 * Manages users, departments, roles, and permissions for the DataViz platform.
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.dataviz.user.mapper")
@ComponentScan(basePackages = {"com.dataviz.user", "com.dataviz.common"})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
