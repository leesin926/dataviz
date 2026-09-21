package com.dataviz.etl.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * ETL任务创建DTO
 */
@Data
public class EtlTaskCreateDTO {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "源数据源ID不能为空")
    private Long sourceDatasourceId;

    @NotNull(message = "目标数据源ID不能为空")
    private Long targetDatasourceId;

    @NotBlank(message = "源表名不能为空")
    private String sourceTable;

    @NotBlank(message = "目标表名不能为空")
    private String targetTable;

    /**
     * 转换配置 (JSON格式)
     */
    private String transformConfig;

    /**
     * 调度Cron表达式
     */
    private String scheduleCron;
}
