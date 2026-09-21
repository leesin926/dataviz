package com.dataviz.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 调度任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("schedule_job")
public class ScheduleJob {

    @TableId(type = IdType.AUTO)
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
     * 状态: RUNNING/PAUSED/ERROR
     */
    private String status;

    /**
     * 任务描述
     */
    private String description;

    /**
     * Misfire处理策略: IGNORE/FIRE_ONCE/EXECUTE_ALL
     */
    private String misfirePolicy;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
