package com.dataviz.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application
 * <p>
 * Entry point for the DataViz platform gateway service.
 * Handles routing, authentication, rate limiting, and CORS.
 * Uses Spring WebFlux (reactive stack), NOT servlet-based Spring MVC.
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
