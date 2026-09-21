package com.dataviz.analysis.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报告创建DTO
 */
@Data
public class ReportCreateDTO {

    @NotBlank(message = "报告名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    private Long datasetId;

    /**
     * 图表配置 (JSON格式)
     */
    private String config;
}
