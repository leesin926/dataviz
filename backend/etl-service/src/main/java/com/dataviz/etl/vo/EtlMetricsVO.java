package com.dataviz.etl.vo;

import lombok.Data;

/**
 * ETL任务指标VO
 */
@Data
public class EtlMetricsVO {

    private Long taskId;
    private String taskName;
    private String status;
    private Integer totalRuns;
    private Integer successRuns;
    private Integer failedRuns;
    private Long totalRecordsRead;
    private Long totalRecordsWritten;
    private Double avgDurationMs;
    private String lastRunStatus;
}
