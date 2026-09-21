package com.dataviz.openapi.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVO {

    private Long id;

    private Long tenantId;

    private String appName;

    private String appKey;

    private String appSecret;

    private String permissions;

    private Integer rateLimit;

    private String ipWhitelist;

    private Integer status;

    private LocalDateTime createTime;
}
