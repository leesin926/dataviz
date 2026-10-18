package com.dataviz.openapi.dto;

import lombok.Data;

@Data
public class WebhookCreateDTO {

    /**
     * 回调记录 ID。{@code PUT /api/openapi/webhook} 靠它定位要改哪一行；
     * 创建时可以不传，由自增主键决定。
     */
    private Long id;

    private Long appId;

    private String eventType;

    private String url;

    private String secret;
}
