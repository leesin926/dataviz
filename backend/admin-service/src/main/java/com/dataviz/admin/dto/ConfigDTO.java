package com.dataviz.admin.dto;

import lombok.Data;

@Data
public class ConfigDTO {

    private String configKey;

    private String configValue;

    private String configType;

    private String remark;
}
