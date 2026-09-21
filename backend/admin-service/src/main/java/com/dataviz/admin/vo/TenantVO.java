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
public class TenantVO {

    private Long id;

    private String name;

    private String code;

    /**
     * ACTIVE / DISABLED
     */
    private String status;

    private String contactName;

    private String contactPhone;

    private LocalDateTime expireTime;

    private Integer maxUsers;

    private String config;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
