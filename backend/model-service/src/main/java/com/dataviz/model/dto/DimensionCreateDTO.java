package com.dataviz.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 维度创建DTO
 */
@Data
public class DimensionCreateDTO {

    @NotBlank(message = "维度名称不能为空")
    private String name;

    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    private String dataType;

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotBlank(message = "表名不能为空")
    private String tableName;

    @NotBlank(message = "列名不能为空")
    private String columnName;

    private String description;
}
