package com.dataviz.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantUpdateDTO {

    private Long id;

    private String name;

    private String contactName;

    private String contactPhone;

    private LocalDateTime expireTime;

    private Integer maxUsers;

    /**
     * JSON configuration string
     */
    private String config;
}
