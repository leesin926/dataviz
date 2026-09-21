package com.dataviz.datasource.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源创建DTO
 */
@Data
public class DatasourceCreateDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotNull(message = "数据源类型不能为空")
    private String type;

    @NotNull(message = "连接配置不能为空")
    private String config;

    private String description;
}
