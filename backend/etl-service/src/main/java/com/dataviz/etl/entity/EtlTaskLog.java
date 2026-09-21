package com.dataviz.etl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ETL任务执行日志实体
 */
@Data
@TableName("etl_task_log")
public class EtlTaskLog implements Serializable {

    
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 读取记录数
     */
    private Long recordsRead;

    /**
     * 写入记录数
     */
    private Long recordsWritten;

    /**
     * 错误信息
     */
    private String errorMessage;
}
