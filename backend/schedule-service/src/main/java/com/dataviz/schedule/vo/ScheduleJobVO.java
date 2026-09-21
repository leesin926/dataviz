package com.dataviz.schedule.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 调度任务VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJobVO {

    private Long id;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务分组
     */
    private String jobGroup;

    /**
     * Cron表达式
     */
    private String cronExpression;

    /**
     * 任务执行类
     */
    private String jobClass;

    /**
     * 任务参数（JSON格式）
     */
    private String jobParams;

    /**
     * 状态: RUNNING/PAUSED/ERROR
     */
    private String status;

    /**
     * 任务描述
     */
    private String description;

    /**
     * Misfire处理策略
     */
    private String misfirePolicy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
