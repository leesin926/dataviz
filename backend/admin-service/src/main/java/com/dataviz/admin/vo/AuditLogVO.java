package com.dataviz.admin.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogVO {

    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String action;

    private String method;

    private String requestUrl;

    private String requestParams;

    private Integer responseCode;

    private String ip;

    private String userAgent;

    /**
     * Execution time in milliseconds
     */
    private Long executionTime;

    private Long tenantId;

    private LocalDateTime createTime;
}
