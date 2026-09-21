package com.dataviz.openapi.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookVO {

    private Long id;

    private Long tenantId;

    private Long appId;

    private String eventType;

    private String url;

    private String secret;

    private Integer status;
}
