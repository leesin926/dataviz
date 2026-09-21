package com.dataviz.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 指标创建DTO
 */
@Data
public class MetricCreateDTO {

    @NotBlank(message = "指标名称不能为空")
    private String name;

    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    private String expression;

    @NotBlank(message = "聚合类型不能为空")
    private String aggregationType;

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotBlank(message = "表名不能为空")
    private String tableName;

    private String description;
}
