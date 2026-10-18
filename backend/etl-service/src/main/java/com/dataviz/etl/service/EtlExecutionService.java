package com.dataviz.etl.service;

/**
 * ETL执行服务接口 - 负责实际的数据抽取、转换、加载
 */
public interface EtlExecutionService {

    /**
     * 异步执行ETL任务
     */
    void executeTask(Long taskId);

    /**
     * 请求停止该任务正在跑的这一次执行。
     * <p>
     * 只是把停止意图登记上，正在执行的线程会在层与层之间看到它；已经提交给数据源的写入不会被撤回。
     */
    void cancelTask(Long taskId);

    /**
     * 该任务当前有没有正在跑的这一次执行。
     * <p>
     * 判据是"正在跑"，不是 status 字段：status 记的是这个任务开没开（要不要按 cron 跑），
     * 演示数据里 RUNNING 是常态。拿 status 当互斥判据的话，一个开着的服务永远点不动「启动」，
     * 而正在跑的那一次反而没人拦 —— 执行入口自己会再挡一次，这里的值只用来给调用方一句准话。
     */
    boolean isRunning(Long taskId);
}
