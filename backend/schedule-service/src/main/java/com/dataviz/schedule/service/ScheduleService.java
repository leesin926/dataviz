package com.dataviz.schedule.service;

/**
 * 调度器侧的触发登记 —— 只负责"这个 jobId 现在有没有 cron 在打"，
 * 任务的真相（cron、状态、参数）始终在 {@code schedule_job} 表里。
 *
 * <p>这里刻意<b>没有</b> pause/resume：原先那两个方法靠
 * {@code pausedTasks}/{@code pausedCronMap} 两个 Map 存"暂停前的样子"，
 * 而 {@code pauseJob} 从来往不往里写 —— 于是"恢复"永远查不到东西、
 * 只打一条 warn，数据库里却已经把状态改成了 RUNNING。
 * 现在恢复由业务层直接从库里重建（{@link #registerJob}），没有第二份状态需要维护。</p>
 */
public interface ScheduleService {

    /**
     * 登记/重建一个 cron 触发。同一个 jobId 重复调用是安全的：先撤掉旧的再挂新的，
     * 不会留下两个都在打的触发器（这正是"编辑后重复触发"的形态）。
     *
     * @throws com.dataviz.common.core.exception.BizException cron 不合法时抛出，
     *         且不登记任何触发 —— 绝不"注册失败但状态写着运行中"
     */
    void registerJob(Long jobId, String cronExpression, Runnable task);

    /** 撤掉某个 jobId 的触发（暂停与删除都走这里）；本来就没登记时静默返回 */
    void removeJob(Long jobId);

    /** 立即触发一次，不影响已登记的 cron */
    void triggerOnce(Long jobId, Runnable task);
}
