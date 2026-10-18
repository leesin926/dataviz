package com.dataviz.schedule.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.schedule.service.ScheduleService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * 调度服务实现 - 基于Spring的ThreadPoolTaskScheduler
 *
 * <p>触发状态只存一份（{@link #scheduledFutures}）。重启后由业务层按库里的
 * {@code RUNNING} 行重新登记，所以"库里写着运行中、其实一个触发器都没有"
 * 这一形态不再可能 —— 那原先是必然发生的：{@code scheduledFutures} 是内存 Map，
 * 而启动时没有任何装载器。</p>
 */
@Slf4j
@Service
public class ScheduleServiceImpl implements ScheduleService {

    private TaskScheduler taskScheduler;

    /** jobId -> 已登记的触发器。这是"当前有没有 cron 在打"的唯一真相 */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

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

    /**
     * 进程退出前取消全部触发。没有这一步的话，一个正在跑的 long job 会在
     * Spring 关闭线程池之后才被中断，日志里看到的是"任务执行失败: null"。
     */
    @PreDestroy
    public void shutdown() {
        int armed = scheduledFutures.size();
        for (Map.Entry<Long, ScheduledFuture<?>> entry : scheduledFutures.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledFutures.clear();
        if (taskScheduler instanceof ThreadPoolTaskScheduler) {
            ((ThreadPoolTaskScheduler) taskScheduler).shutdown();
        }
        log.info("调度服务已关闭，取消 {} 个触发器", armed);
    }

    @Override
    public void registerJob(Long jobId, String cronExpression, Runnable task) {
        // 先解析再登记：解析失败时一个触发器都不会被挂上，调用方的事务回滚后库里不会出现"运行中但没人打"
        CronTrigger trigger;
        try {
            trigger = new CronTrigger(cronExpression.trim());
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "Cron 表达式不合法: " + cronExpression + "（Spring 需要 6 段式，如 0 0/30 * * * ?）");
        }
        cancelQuietly(jobId);
        ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
        scheduledFutures.put(jobId, future);
        log.info("登记调度任务: jobId={}, cron={}", jobId, cronExpression);
    }

    @Override
    public void removeJob(Long jobId) {
        cancelQuietly(jobId);
        log.info("移除调度任务: jobId={}", jobId);
    }

    @Override
    public void triggerOnce(Long jobId, Runnable task) {
        taskScheduler.schedule(task, java.time.Instant.now());
        log.info("立即触发任务: jobId={}", jobId);
    }

    private void cancelQuietly(Long jobId) {
        ScheduledFuture<?> future = scheduledFutures.remove(jobId);
        if (future != null) {
            // cancel(false)：已经在跑的那一次让它跑完并正常记日志，只阻止后续触发
            future.cancel(false);
        }
    }
}
