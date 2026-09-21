package com.dataviz.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据集创建DTO
 */
@Data
public class DatasetCreateDTO {

    @NotBlank(message = "数据集名称不能为空")
    private String name;

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    private String tableName;

    private String sqlQuery;

    /**
     * 维度配置 (JSON数组)
     */
    private String dimensions;

    /**
     * 指标配置 (JSON数组)
     */
    private String metrics;

    /**
     * 过滤条件 (JSON数组)
     */
    private String filters;
}
