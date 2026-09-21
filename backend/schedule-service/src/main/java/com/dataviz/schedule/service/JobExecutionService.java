package com.dataviz.schedule.service;

/**
 * 任务执行服务接口 - 异步执行任务并记录结果
 */
public interface JobExecutionService {

    /**
     * 异步执行任务
     *
     * @param jobId   任务ID
     * @param jobName 任务名称
     * @param jobClass 执行类名
     * @param jobParams 任务参数JSON
     */
    void executeJob(Long jobId, String jobName, String jobClass, String jobParams);
}
