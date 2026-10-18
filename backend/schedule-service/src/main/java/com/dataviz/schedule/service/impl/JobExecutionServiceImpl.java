package com.dataviz.schedule.service.impl;

import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.enums.JobLogStatus;
import com.dataviz.schedule.job.JobRegistry;
import com.dataviz.schedule.job.ScheduledJob;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.service.JobExecutionService;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 任务执行服务实现 - 异步执行并记录日志
 *
 * <p>与上一版的两条实质差别：</p>
 * <ol>
 *   <li><b>不再反射任意类</b>。原先是 {@code Class.forName(jobClass).newInstance()} 再调
 *       {@code execute(String)}，{@code jobClass} 由前端自由文本填入 ⇒ 拿到
 *       {@code schedule:write} 就能实例化 classpath 上任意有无参构造的类。
 *       现在只认 {@link JobRegistry} 里注册过的 Spring Bean。</li>
 *   <li><b>没真跑到就不写 SUCCESS</b>。原先 {@code jobClass} 为空时整段 try 什么都不做，
 *       直接落到"任务执行成功"；有 {@code execute(String)} 签名对不上时只 {@code log.warn}
 *       一句也照样记成功。现在这两种都是 FAILED，并且 message 说清楚为什么。
 *       （和 ETL 那边"读写各 1000 行"的假账是同一形态，见 API-24。）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionServiceImpl implements JobExecutionService {

    private final ScheduleJobLogMapper scheduleJobLogMapper;
    private final JobRegistry jobRegistry;

    @Async("jobExecutorThreadPool")
    @Override
    public void executeJob(Long jobId, String jobName, String jobClass, String jobParams) {
        LocalDateTime startTime = LocalDateTime.now();
        ScheduleJobLog jobLog = new ScheduleJobLog();
        jobLog.setJobId(jobId);
        jobLog.setJobName(jobName);
        jobLog.setStartTime(startTime);

        String outcome;
        try {
            ScheduledJob handler = jobRegistry.require(jobClass);
            handler.execute(jobParams);
            outcome = "已执行 " + handler.key() + "：" + handler.label();
            jobLog.setStatus(JobLogStatus.SUCCESS.getCode());
            jobLog.setMessage(outcome);
            log.info("任务执行成功: jobId={}, jobName={}, handler={}, 耗时={}ms", jobId, jobName,
                    handler.key(), Duration.between(startTime, LocalDateTime.now()).toMillis());
        } catch (Exception e) {
            jobLog.setStatus(JobLogStatus.FAILED.getCode());
            jobLog.setMessage("任务执行失败: " + describe(e));
            log.error("任务执行失败: jobId={}, jobName={}, jobClass={}", jobId, jobName, jobClass, e);
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            jobLog.setEndTime(endTime);
            jobLog.setCreateTime(endTime);
            scheduleJobLogMapper.insert(jobLog);
        }
    }

    /**
     * 参数写错这一类（BizException/IllegalArgumentException）本身带着"为什么不行"，
     * 直接把它交给运维看；其余异常才退回类名，避免日志里只剩一句 null。
     */
    private String describe(Exception e) {
        String message = e.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return e.getClass().getName();
    }
}
