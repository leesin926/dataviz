package com.dataviz.openapi.dto;

import lombok.Data;

@Data
public class AppCreateDTO {

    private String appName;

    private String permissions;

    private Integer rateLimit;

    private String ipWhitelist;
}
