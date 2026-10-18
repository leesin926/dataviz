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
     * {@code job_class} 是否还能解析到注册过的处理器。
     *
     * <p>{@code false} 意味着这条任务每次触发只会留下一条 FAILED 日志。演示数据里
     * 那三条 {@code com.dataviz.schedule.job.*Job} 就是这个状态 —— 类从来没有存在过。
     * 把它显出来，列表才不会把一条永远跑不起来的任务显示成一条正常任务。</p>
     */
    private Boolean handlerRegistered;

    /**
     * 处理器的中文名；{@code handlerRegistered=false} 时为 null
     */
    private String handlerLabel;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
