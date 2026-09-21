package com.dataviz.schedule.service.impl;

import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.enums.JobLogStatus;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 任务执行服务实现 - 异步执行并记录日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionServiceImpl implements JobExecutionService {

    private final ScheduleJobLogMapper scheduleJobLogMapper;

    @Async("jobExecutorThreadPool")
    @Override
    public void executeJob(Long jobId, String jobName, String jobClass, String jobParams) {
        LocalDateTime startTime = LocalDateTime.now();
        ScheduleJobLog jobLog = new ScheduleJobLog();
        jobLog.setJobId(jobId);
        jobLog.setJobName(jobName);
        jobLog.setStartTime(startTime);

        try {
            log.info("开始执行任务: jobId={}, jobName={}, jobClass={}", jobId, jobName, jobClass);

            // 通过反射加载并执行任务类
            if (jobClass != null && !jobClass.isEmpty()) {
                Class<?> clazz = Class.forName(jobClass);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof Runnable) {
                    ((Runnable) instance).run();
                } else {
                    // 尝试调用execute方法
                    try {
                        java.lang.reflect.Method method = clazz.getMethod("execute", String.class);
                        method.invoke(instance, jobParams);
                    } catch (NoSuchMethodException e) {
                        log.warn("任务类无execute方法，尝试直接调用run: {}", jobClass);
                    }
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            jobLog.setEndTime(endTime);
            jobLog.setStatus(JobLogStatus.SUCCESS.getCode());
            jobLog.setMessage("任务执行成功");
            log.info("任务执行成功: jobId={}, jobName={}, 耗时={}ms", jobId, jobName,
                    java.time.Duration.between(startTime, endTime).toMillis());

        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            jobLog.setEndTime(endTime);
            jobLog.setStatus(JobLogStatus.FAILED.getCode());
            jobLog.setMessage("任务执行失败: " + e.getMessage());
            log.error("任务执行失败: jobId={}, jobName={}", jobId, jobName, e);
        } finally {
            jobLog.setCreateTime(LocalDateTime.now());
            scheduleJobLogMapper.insert(jobLog);
        }
    }
}
