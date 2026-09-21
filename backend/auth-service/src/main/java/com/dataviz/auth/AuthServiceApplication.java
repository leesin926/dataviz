package com.dataviz.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * Authentication Service Application
 * <p>
 * Handles user authentication, JWT token management, captcha,
 * and SSO integration for the DataViz platform.
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.dataviz.auth.mapper")
@ComponentScan(basePackages = {"com.dataviz.auth", "com.dataviz.common"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
