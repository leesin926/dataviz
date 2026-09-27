package com.dataviz.etl.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
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

/**
 * ETL执行服务实现 - 异步执行ETL任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlExecutionServiceImpl implements EtlExecutionService {

    private final EtlTaskMapper etlTaskMapper;
    private final EtlTaskLogMapper etlTaskLogMapper;

    @Async
    @Override
    public void executeTask(Long taskId) {
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

        try {
            // API-24：这里原本"读 1000 行、写回同一个数"，日志与任务状态双双记成 SUCCESS —— 伪成功记账。
            // 真执行要接源端批读 + 目标端批量写（#75 的写端点设计稿，等用户拍板）。本轮先把账停掉，
            // 处理方式与 API-20 的 Email/SMS 通知一致：显式 503、日志记 FAILED，让"没做"在数据上看得出来。
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "ETL execution is not implemented: source read and target write are not wired up yet");
        } catch (Exception e) {
            log.error("ETL task execution failed: taskId={}", taskId, e);

            // 更新日志状态为失败
            taskLog.setStatus("FAILED");
            taskLog.setEndTime(LocalDateTime.now());
            taskLog.setErrorMessage(e.getMessage());
            etlTaskLogMapper.updateById(taskLog);

            // 更新任务状态为错误
            task.setStatus(EtlTaskStatus.ERROR.name());
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus("FAILED");
            etlTaskMapper.updateById(task);
        }
    }
}
