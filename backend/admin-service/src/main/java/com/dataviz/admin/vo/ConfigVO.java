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
public class ConfigVO {

    private Long id;

    private String configKey;

    private String configValue;

    /**
     * SYSTEM / CUSTOM
     */
    private String configType;

    private String remark;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
