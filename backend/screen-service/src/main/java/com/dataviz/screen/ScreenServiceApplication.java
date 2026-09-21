package com.dataviz.screen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.dataviz.screen", "com.dataviz.common"})
public class ScreenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScreenServiceApplication.class, args);
    }
}
