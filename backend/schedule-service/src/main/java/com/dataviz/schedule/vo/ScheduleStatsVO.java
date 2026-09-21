package com.dataviz.schedule.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度任务统计VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleStatsVO {

    /**
     * 总任务数
     */
    private Integer totalJobs;

    /**
     * 运行中任务数
     */
    private Integer runningJobs;

    /**
     * 已暂停任务数
     */
    private Integer pausedJobs;

    /**
     * 异常任务数
     */
    private Integer errorJobs;

    /**
     * 今日执行次数
     */
    private Long todayExecutions;

    /**
     * 今日成功次数
     */
    private Long todaySuccess;

    /**
     * 今日失败次数
     */
    private Long todayFailed;
}
