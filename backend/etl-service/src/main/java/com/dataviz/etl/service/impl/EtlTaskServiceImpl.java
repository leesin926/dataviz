package com.dataviz.etl.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.etl.dto.EtlTaskCreateDTO;
import com.dataviz.etl.dto.EtlTaskUpdateDTO;
import com.dataviz.etl.entity.EtlTask;
import com.dataviz.etl.entity.EtlTaskLog;
import com.dataviz.etl.entity.EtlTaskStatus;
import com.dataviz.etl.mapper.EtlTaskLogMapper;
import com.dataviz.etl.mapper.EtlTaskMapper;
import com.dataviz.etl.service.EtlExecutionService;
import com.dataviz.etl.service.EtlTaskService;
import com.dataviz.etl.vo.EtlMetricsVO;
import com.dataviz.etl.vo.EtlTaskLogVO;
import com.dataviz.etl.vo.EtlTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ETL任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtlTaskServiceImpl implements EtlTaskService {

    private final EtlTaskMapper etlTaskMapper;
    private final EtlTaskLogMapper etlTaskLogMapper;
    private final EtlExecutionService etlExecutionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(EtlTaskCreateDTO dto, String tenantId) {
        EtlTask task = new EtlTask();
        BeanUtils.copyProperties(dto, task);
        task.setTenantId(tenantId);
        task.setStatus(EtlTaskStatus.STOPPED.name());

        etlTaskMapper.insert(task);
        log.info("Created ETL task: {} (id={})", task.getName(), task.getId());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long id, EtlTaskUpdateDTO dto) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        // 运行中的任务不允许修改
        if (EtlTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BizException("运行中的任务不允许修改");
        }
        BeanUtils.copyProperties(dto, task);
        etlTaskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        if (EtlTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BizException("运行中的任务不允许删除");
        }
        etlTaskMapper.deleteById(id);
    }

    @Override
    public EtlTaskVO getTaskById(Long id) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        EtlTaskVO vo = new EtlTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    @Override
    public PageResult<EtlTaskVO> listTasks(String name, String status, String tenantId, PageQuery pageQuery) {
        Page<EtlTask> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<EtlTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlTask::getTenantId, tenantId);
        if (StringUtils.hasText(name)) {
            wrapper.like(EtlTask::getName, name);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(EtlTask::getStatus, status);
        }
        wrapper.orderByDesc(EtlTask::getCreateTime);

        Page<EtlTask> result = etlTaskMapper.selectPage(page, wrapper);
        List<EtlTaskVO> voList = result.getRecords().stream().map(task -> {
            EtlTaskVO vo = new EtlTaskVO();
            BeanUtils.copyProperties(task, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public void startTask(Long id) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        if (EtlTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BizException("任务已在运行中");
        }
        task.setStatus(EtlTaskStatus.RUNNING.name());
        etlTaskMapper.updateById(task);

        // 异步执行任务
        etlExecutionService.executeTask(id);
    }

    @Override
    public void stopTask(Long id) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        task.setStatus(EtlTaskStatus.STOPPED.name());
        etlTaskMapper.updateById(task);
    }

    @Override
    public void pauseTask(Long id) {
        EtlTask task = etlTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }
        if (!EtlTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BizException("只有运行中的任务才能暂停");
        }
        task.setStatus(EtlTaskStatus.PAUSED.name());
        etlTaskMapper.updateById(task);
    }

    @Override
    public List<EtlTaskLogVO> getTaskLogs(Long taskId) {
        LambdaQueryWrapper<EtlTaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlTaskLog::getTaskId, taskId);
        wrapper.orderByDesc(EtlTaskLog::getStartTime);

        return etlTaskLogMapper.selectList(wrapper).stream().map(logEntry -> {
            EtlTaskLogVO vo = new EtlTaskLogVO();
            BeanUtils.copyProperties(logEntry, vo);
            if (logEntry.getStartTime() != null && logEntry.getEndTime() != null) {
                vo.setDuration(Duration.between(logEntry.getStartTime(), logEntry.getEndTime()).toMillis());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public EtlMetricsVO getTaskMetrics(Long taskId) {
        EtlTask task = etlTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.ETL_TASK_NOT_FOUND);
        }

        List<EtlTaskLog> logs = etlTaskLogMapper.selectList(
                new LambdaQueryWrapper<EtlTaskLog>().eq(EtlTaskLog::getTaskId, taskId));

        EtlMetricsVO metrics = new EtlMetricsVO();
        metrics.setTaskId(taskId);
        metrics.setTaskName(task.getName());
        metrics.setStatus(task.getStatus());
        metrics.setTotalRuns(logs.size());
        metrics.setSuccessRuns((int) logs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count());
        metrics.setFailedRuns((int) logs.stream().filter(l -> "FAILED".equals(l.getStatus())).count());
        metrics.setTotalRecordsRead(logs.stream().filter(l -> l.getRecordsRead() != null)
                .mapToLong(EtlTaskLog::getRecordsRead).sum());
        metrics.setTotalRecordsWritten(logs.stream().filter(l -> l.getRecordsWritten() != null)
                .mapToLong(EtlTaskLog::getRecordsWritten).sum());
        metrics.setLastRunStatus(task.getLastRunStatus());

        // 计算平均执行时长
        double avgDuration = logs.stream()
                .filter(l -> l.getStartTime() != null && l.getEndTime() != null)
                .mapToDouble(l -> Duration.between(l.getStartTime(), l.getEndTime()).toMillis())
                .average()
                .orElse(0.0);
        metrics.setAvgDurationMs(avgDuration);

        return metrics;
    }
}
