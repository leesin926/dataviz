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
            // 1. 从源数据源读取数据
            long recordsRead = readFromSource(task);
            taskLog.setRecordsRead(recordsRead);

            // 2. 数据转换
            // TODO: 根据transformConfig进行数据转换

            // 3. 写入目标数据源
            long recordsWritten = writeToTarget(task, recordsRead);
            taskLog.setRecordsWritten(recordsWritten);

            // 更新日志状态为成功
            taskLog.setStatus("SUCCESS");
            taskLog.setEndTime(LocalDateTime.now());
            etlTaskLogMapper.updateById(taskLog);

            // 更新任务状态
            task.setStatus(EtlTaskStatus.COMPLETED.name());
            task.setLastRunTime(LocalDateTime.now());
            task.setLastRunStatus("SUCCESS");
            etlTaskMapper.updateById(task);

            log.info("ETL task completed successfully: taskId={}, read={}, written={}",
                    taskId, recordsRead, recordsWritten);

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

    /**
     * 从源数据源读取数据
     * TODO: 实现实际的数据读取逻辑，根据sourceDatasourceId连接数据源并读取sourceTable数据
     */
    private long readFromSource(EtlTask task) {
        log.info("Reading data from source: datasourceId={}, table={}",
                task.getSourceDatasourceId(), task.getSourceTable());
        // 模拟读取数据
        return 1000L;
    }

    /**
     * 写入目标数据源
     * TODO: 实现实际的写入逻辑，根据targetDatasourceId连接数据源并写入targetTable
     */
    private long writeToTarget(EtlTask task, long recordsRead) {
        log.info("Writing data to target: datasourceId={}, table={}",
                task.getTargetDatasourceId(), task.getTargetTable());
        // 模拟写入数据 (假设转换后记录数不变)
        return recordsRead;
    }
}
