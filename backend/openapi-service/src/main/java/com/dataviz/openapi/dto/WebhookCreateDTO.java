package com.dataviz.openapi.dto;

import lombok.Data;

@Data
public class WebhookCreateDTO {

    private Long appId;

    private String eventType;

    private String url;

    private String secret;
}
