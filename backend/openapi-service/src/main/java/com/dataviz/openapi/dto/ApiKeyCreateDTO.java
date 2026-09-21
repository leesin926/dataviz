package com.dataviz.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ApiKeyCreateDTO {

    private String appName;

    /**
     * Requests per minute limit
     */
    private Integer rateLimit;

    /**
     * Allowed IP addresses
     */
    private List<String> allowedIps;

    private LocalDateTime expireTime;
}
