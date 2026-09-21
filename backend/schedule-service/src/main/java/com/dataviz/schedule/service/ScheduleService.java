package com.dataviz.schedule.service;

/**
 * 调度服务接口 - 集成Spring调度框架
 */
public interface ScheduleService {

    /**
     * 注册定时任务到调度器
     */
    void registerJob(Long jobId, String cronExpression, Runnable task);

    /**
     * 从调度器移除任务
     */
    void removeJob(Long jobId);

    /**
     * 暂停调度器中的任务
     */
    void pauseJob(Long jobId);

    /**
     * 恢复调度器中的任务
     */
    void resumeJob(Long jobId);

    /**
     * 立即执行一次任务
     */
    void triggerOnce(Long jobId, Runnable task);
}
