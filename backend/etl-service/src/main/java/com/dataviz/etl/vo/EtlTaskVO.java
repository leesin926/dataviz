package com.dataviz.etl.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ETL任务VO
 */
@Data
public class EtlTaskVO {

    private Long id;
    private String name;
    private String description;
    private Long sourceDatasourceId;
    private Long targetDatasourceId;
    private String sourceTable;
    private String targetTable;
    private String transformConfig;
    private String scheduleCron;
    private String status;
    private LocalDateTime lastRunTime;
    private String lastRunStatus;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
