package com.dataviz.etl.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EtlTaskInstanceVO {
    private Long id;
    private Long taskId;
    private String triggerType;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String log;
}
