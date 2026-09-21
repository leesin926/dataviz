package com.dataviz.schedule.service.impl;

import com.dataviz.schedule.service.ScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 调度服务实现 - 基于Spring的ThreadPoolTaskScheduler
 */
@Slf4j
@Service
public class ScheduleServiceImpl implements ScheduleService {

    private TaskScheduler taskScheduler;

    /**
     * 存储已注册的任务：jobId -> ScheduledFuture
     */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /**
     * 存储被暂停的任务：jobId -> Runnable
     */
    private final Map<Long, Runnable> pausedTasks = new ConcurrentHashMap<>();

    /**
     * 存储被暂停任务的cron表达式：jobId -> cron
     */
    private final Map<Long, String> pausedCronMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("schedule-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        this.taskScheduler = scheduler;
        log.info("调度服务初始化完成，线程池大小: 10");
    }

    @Override
    public void registerJob(Long jobId, String cronExpression, Runnable task) {
        try {
            CronTrigger trigger = new CronTrigger(cronExpression);
            ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
            scheduledFutures.put(jobId, future);
            log.info("注册调度任务成功: jobId={}, cron={}", jobId, cronExpression);
        } catch (Exception e) {
            log.error("注册调度任务失败: jobId={}, cron={}", jobId, cronExpression, e);
        }
    }

    @Override
    public void removeJob(Long jobId) {
        ScheduledFuture<?> future = scheduledFutures.remove(jobId);
        if (future != null) {
            future.cancel(true);
        }
        pausedTasks.remove(jobId);
        pausedCronMap.remove(jobId);
        log.info("移除调度任务: jobId={}", jobId);
    }

    @Override
    public void pauseJob(Long jobId) {
        ScheduledFuture<?> future = scheduledFutures.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("暂停调度任务: jobId={}", jobId);
        }
        // 注意: 暂停时需要保存cron和task信息以便恢复
        // 实际场景中可从数据库重新加载
    }

    @Override
    public void resumeJob(Long jobId) {
        Runnable task = pausedTasks.remove(jobId);
        String cron = pausedCronMap.remove(jobId);
        if (task != null && cron != null) {
            registerJob(jobId, cron, task);
            log.info("恢复调度任务: jobId={}", jobId);
        } else {
            log.warn("恢复任务失败，未找到暂停的任务信息: jobId={}", jobId);
        }
    }

    @Override
    public void triggerOnce(Long jobId, Runnable task) {
        taskScheduler.schedule(task, java.time.Instant.now());
        log.info("立即触发任务: jobId={}", jobId);
    }
}
