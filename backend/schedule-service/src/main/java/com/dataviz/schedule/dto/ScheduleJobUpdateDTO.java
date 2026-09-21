package com.dataviz.schedule.dto;

import lombok.Data;

/**
 * 调度任务更新DTO
 */
@Data
public class ScheduleJobUpdateDTO {

    /**
     * 任务ID
     */
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
     * 任务执行类（全限定类名）
     */
    private String jobClass;

    /**
     * 任务参数（JSON格式）
     */
    private String jobParams;

    /**
     * 任务描述
     */
    private String description;

    /**
     * Misfire处理策略: IGNORE/FIRE_ONCE/EXECUTE_ALL
     */
    private String misfirePolicy;
}
