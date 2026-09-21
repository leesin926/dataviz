package com.dataviz.etl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("etl_task_instance")
public class EtlTaskInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** Trigger type: manual, cron, api */
    private String triggerType;
    /** Status: 1=running, 2=success, 3=failed, 4=stopped */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** Execution log */
    private String log;
}
