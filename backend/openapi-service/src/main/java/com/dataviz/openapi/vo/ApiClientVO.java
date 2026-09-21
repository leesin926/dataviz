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
public class ApiClientVO {

    private Long id;

    private String appName;

    private String appKey;

    /**
     * ACTIVE / DISABLED
     */
    private String status;

    private Integer rateLimit;

    private String allowedIps;

    private LocalDateTime expireTime;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
