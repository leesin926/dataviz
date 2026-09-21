package com.dataviz.etl.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ETL任务日志VO
 */
@Data
public class EtlTaskLogVO {

    private Long id;
    private Long taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long recordsRead;
    private Long recordsWritten;
    private String errorMessage;
    /**
     * 执行时长(毫秒)
     */
    private Long duration;
}
