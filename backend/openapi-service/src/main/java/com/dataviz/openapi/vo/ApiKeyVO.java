package com.dataviz.openapi.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyVO {

    /**
     * Client ID
     */
    private Long clientId;

    /**
     * Application name
     */
    private String appName;

    /**
     * App key (always returned)
     */
    private String appKey;

    /**
     * App secret (only returned on creation/reset)
     */
    private String appSecret;
}
