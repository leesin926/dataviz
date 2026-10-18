package com.dataviz.etl.service.impl;

import com.dataviz.etl.engine.DagExecutionException;
import com.dataviz.etl.engine.DagExecutor;
import com.dataviz.etl.engine.DagRunSummary;
import com.dataviz.etl.entity.EtlTask;
import com.dataviz.etl.entity.EtlTaskLog;
import com.dataviz.etl.entity.EtlTaskStatus;
import com.dataviz.etl.mapper.EtlTaskLogMapper;
import com.dataviz.etl.mapper.EtlTaskMapper;
import com.dataviz.etl.service.EtlExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETL执行服务实现 - 异步跑 DAG 并按真实读数记账
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlExecutionServiceImpl implements EtlExecutionService {

    private final EtlTaskMapper etlTaskMapper;
    private final EtlTaskLogMapper etlTaskLogMapper;
    private final DagExecutor dagExecutor;

    /** 正在跑的任务；执行入口自己把守，界面上的「启动」只是提前给一句准话 */
    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isRunning(Long taskId) {
        return runningTasks.contains(taskId);
    }

    @Async
    @Override
    public void executeTask(Long taskId) {
        // 两次连点会并发跑同一个 DAG：第二个线程往同一张目标表再插一遍，症状是数据翻倍而没有报错
        if (!runningTasks.add(taskId)) {
            log.warn("ETL task {} 的上一次执行还没结束，本次启动已忽略", taskId);
            return;
        }
        try {
            run(taskId);
        } finally {
            runningTasks.remove(taskId);
        }
    }

    private void run(Long taskId) {
        log.info("Starting ETL task execution: taskId={}", taskId);

        EtlTask task = etlTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("ETL task not found: {}", taskId);
            return;
        }

        // 创建执行日志
        EtlTaskLog taskLog = new EtlTaskLog();
        taskLog.setTaskId(taskId);
        taskLog.setStartTime(LocalDateTime.now());
        taskLog.setStatus("RUNNING");
        taskLog.setRecordsRead(0L);
        taskLog.setRecordsWritten(0L);
        etlTaskLogMapper.insert(taskLog);

        String dagJson = task.getTransformConfig();
        if (dagJson == null || dagJson.trim().isEmpty()) {
            finishFailed(task, taskLog, "任务没有配置转换流程（transform_config 为空），设计器里的 DAG 要先保存", null);
            return;
        }

        try {
            // 跟踪键用任务ID：界面上的「停止」只有 taskId，能停掉正在跑的这一次就是它的用途
            DagRunSummary summary = dagExecutor.execute(dagJson, taskId);

            taskLog.setEndTime(LocalDateTime.now());
            taskLog.setRecordsRead(summary.getRowsRead());
            taskLog.setRecordsWritten(summary.getRowsWritten());
            if (summary.isCancelled()) {
                taskLog.setStatus("CANCELLED");
                taskLog.setErrorMessage("执行被停止：计划 " + summary.getPlannedNodes()
                        + " 个节点，完成 " + summary.getExecutedNodes() + " 个");
            } else {
                taskLog.setStatus("SUCCESS");
                // 跑完了但结果不完整（源端读满上限）时借这个字段显形。历史列表按 errorMessage
                // 拼出「错误: xxx」，所以前端那边要跟着改成只在失败时才叫错误 —— 见 etl.ts 的 formatLogLine。
                taskLog.setErrorMessage(summary.getWarnings().isEmpty()
                        ? null : String.join("；", summary.getWarnings()));
            }
            etlTaskLogMapper.updateById(taskLog);

            // 任务本身的 status 不动：那是"要不要按 cron 跑"，一次执行的成败记在 lastRunStatus 上
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus(summary.isCancelled() ? "CANCELLED" : "SUCCESS");
            etlTaskMapper.updateById(task);

            log.info("ETL task finished: taskId={}, read={}, written={}, cancelled={}",
                    taskId, summary.getRowsRead(), summary.getRowsWritten(), summary.isCancelled());

        } catch (Exception e) {
            log.error("ETL task execution failed: taskId={}", taskId, e);
            DagRunSummary partial = e instanceof DagExecutionException
                    ? ((DagExecutionException) e).getPartial() : null;
            finishFailed(task, taskLog, rootMessage(e), partial);
        }
    }

    @Override
    public void cancelTask(Long taskId) {
        dagExecutor.stop(taskId);
    }

    /**
     * 失败落账。
     * <p>
     * 读数照实写：源端读到一半才失败的批次，recordsRead 非 0、recordsWritten 为 0，
     * 这正是"抽出来了但没落库"。都记 0 的话，排查时看不出现在跑过一半，也就看不出目标表有没有半批数据。
     */
    private void finishFailed(EtlTask task, EtlTaskLog taskLog, String message, DagRunSummary partial) {
        taskLog.setStatus("FAILED");
        taskLog.setEndTime(LocalDateTime.now());
        taskLog.setErrorMessage(message);
        if (partial != null) {
            taskLog.setRecordsRead(partial.getRowsRead());
            taskLog.setRecordsWritten(partial.getRowsWritten());
        }
        etlTaskLogMapper.updateById(taskLog);

        task.setStatus(EtlTaskStatus.ERROR.name());
        task.setLastRunTime(LocalDateTime.now());
        task.setLastRunStatus("FAILED");
        etlTaskMapper.updateById(task);
    }

    /**
     * DAG 执行失败时真正的原话挂在最内层 cause 上，外层只是"DAG execution failed: ..."的转述。
     * 记外层等于把"哪个节点为什么失败"从日志里抹掉，只剩一句没有信息量的转述。
     */
    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        StringBuilder chain = new StringBuilder();
        while (cursor != null) {
            if (cursor.getMessage() != null && !cursor.getMessage().trim().isEmpty()) {
                if (chain.length() > 0) {
                    chain.append(" <- ");
                }
                chain.append(cursor.getMessage().trim());
            }
            if (cursor.getCause() == null || cursor.getCause() == cursor) {
                break;
            }
            cursor = cursor.getCause();
        }
        String text = chain.length() == 0 ? e.getClass().getSimpleName() : chain.toString();
        return text.length() > 900 ? text.substring(0, 900) : text;
    }
}
