package com.dataviz.schedule.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 调度任务执行日志VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJobLogVO {

    private Long id;

    /**
     * 任务ID
     */
    private Long jobId;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行状态: SUCCESS/FAILED
     */
    private String status;

    /**
     * 执行消息
     */
    private String message;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
