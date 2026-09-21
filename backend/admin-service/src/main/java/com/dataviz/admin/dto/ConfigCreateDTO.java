package com.dataviz.admin.dto;

import lombok.Data;

@Data
public class ConfigCreateDTO {

    private String configKey;

    private String configValue;

    /**
     * SYSTEM / CUSTOM
     */
    private String configType;

    private String remark;
}
