package com.dataviz.etl.dto;

import lombok.Data;

/**
 * ETL任务更新DTO
 */
@Data
public class EtlTaskUpdateDTO {

    private String name;

    private String description;

    private Long sourceDatasourceId;

    private Long targetDatasourceId;

    private String sourceTable;

    private String targetTable;

    private String transformConfig;

    private String scheduleCron;

    private String status;
}
