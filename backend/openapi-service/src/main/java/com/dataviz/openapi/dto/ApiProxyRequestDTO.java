package com.dataviz.openapi.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ApiProxyRequestDTO {

    /**
     * Application key
     */
    private String appKey;

    /**
     * Application secret
     */
    private String appSecret;

    /**
     * Target API path
     */
    private String apiPath;

    /**
     * HTTP method: GET, POST, PUT, DELETE
     */
    private String method;

    /**
     * Request parameters
     */
    private Map<String, Object> params;

    /**
     * Request body for POST/PUT
     */
    private String body;
}
