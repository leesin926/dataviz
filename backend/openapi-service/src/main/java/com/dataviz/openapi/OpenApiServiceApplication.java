package com.dataviz.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.dataviz.openapi", "com.dataviz.common"})
public class OpenApiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiServiceApplication.class, args);
    }
}
