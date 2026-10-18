package com.dataviz.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.schedule.dto.ScheduleJobCreateDTO;
import com.dataviz.schedule.dto.ScheduleJobUpdateDTO;
import com.dataviz.schedule.entity.ScheduleJob;
import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.enums.JobStatus;
import com.dataviz.schedule.enums.MisfirePolicy;
import com.dataviz.schedule.job.JobRegistry;
import com.dataviz.schedule.job.ScheduledJob;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.mapper.ScheduleJobMapper;
import com.dataviz.schedule.service.JobExecutionService;
import com.dataviz.schedule.service.ScheduleJobService;
import com.dataviz.schedule.service.ScheduleService;
import com.dataviz.schedule.vo.JobHandlerVO;
import com.dataviz.schedule.vo.ScheduleJobLogVO;
import com.dataviz.schedule.vo.ScheduleJobVO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 调度任务服务实现
 *
 * <p>{@code schedule_job} 这张表是任务状态的唯一真相，触发器登记只是它的投影。
 * 四条写路径都按这个规矩来：</p>
 * <ul>
 *   <li>新建 → 校验过才落库，落库即 {@code RUNNING} 并登记触发器；</li>
 *   <li>编辑 → <b>按当前状态决定</b>是否重新登记。原先无条件 remove+register，
 *       于是一个已暂停的任务被编辑一次就开始跑了，而列表里还写着"已暂停"；</li>
 *   <li>暂停 → 撤触发器（不改 cron，随时可恢复）；</li>
 *   <li>恢复 → <b>从库里重建</b>触发器。原先调的是
 *       {@code ScheduleService.resumeJob(id)}，而它读的 {@code pausedTasks}
 *       没有任何写入方 ⇒ 恢复是个空操作，库里写着 RUNNING、实际一个触发器都没有。</li>
 * </ul>
 * <p>另外，{@code scheduledFutures} 是内存 Map 而启动时没有装载器，所以每次重启后
 * 所有 {@code RUNNING} 行都变成"显示运行中、实际不再触发"。现在由
 * {@link #armRunningJobs()} 在就绪时重新投影一遍；处理器已经不存在的那些行置为
 * {@code ERROR} 而不是继续谎称 RUNNING —— 这是 {@code JobStatus.ERROR} 第一次被真正写入。</p>
 *
 * <p>租户：{@code schedule_job} 有 {@code tenant_id} 列，且不在
 * {@code MybatisPlusConfig.IGNORE_TENANT_TABLES} 里，所以登录态下的
 * SELECT/INSERT 由租户拦截器自动收口（本类不重复写条件）；
 * 反过来，启动装载没有会话 ⇒ 拦截器 {@code ignoreTable} 首句直接放行 ⇒
 * 装载器看到的是全租户，这正是调度器需要的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleJobServiceImpl implements ScheduleJobService {

    private final ScheduleJobMapper scheduleJobMapper;
    private final ScheduleJobLogMapper scheduleJobLogMapper;
    private final ScheduleService scheduleService;
    private final JobExecutionService jobExecutionService;
    private final JobRegistry jobRegistry;

    @Override
    @Transactional
    public Long createJob(ScheduleJobCreateDTO createDTO) {
        String jobClass = validateJobClass(createDTO.getJobClass());
        String cron = validateCron(createDTO.getCronExpression());

        ScheduleJob job = new ScheduleJob();
        BeanUtils.copyProperties(createDTO, job);
        job.setJobClass(jobClass);
        job.setCronExpression(cron);
        job.setStatus(JobStatus.RUNNING.getCode());
        if (job.getMisfirePolicy() == null) {
            job.setMisfirePolicy(MisfirePolicy.IGNORE.getCode());
        }
        scheduleJobMapper.insert(job);
        log.info("创建调度任务: id={}, name={}", job.getId(), job.getJobName());

        scheduleService.registerJob(job.getId(), cron, taskOf(job));
        return job.getId();
    }

    @Override
    @Transactional
    public void updateJob(ScheduleJobUpdateDTO updateDTO) {
        ScheduleJob job = requireJob(updateDTO.getId());
        String jobClass = validateJobClass(updateDTO.getJobClass());
        String cron = validateCron(updateDTO.getCronExpression());

        // 状态与租户不在 DTO 里，copyProperties 不会碰它们 —— 暂停中的任务编辑完仍然是暂停，
        // 这正是"编辑不该顺手把任务启动"的语义
        BeanUtils.copyProperties(updateDTO, job);
        job.setJobClass(jobClass);
        job.setCronExpression(cron);
        scheduleJobMapper.updateById(job);
        log.info("更新调度任务: id={}, status={}", job.getId(), job.getStatus());

        if (JobStatus.RUNNING.getCode().equals(job.getStatus())) {
            scheduleService.registerJob(job.getId(), cron, taskOf(job));
        } else {
            scheduleService.removeJob(job.getId());
        }
    }

    @Override
    public ScheduleJobVO getJobById(Long id) {
        return toVO(requireJob(id));
    }

    @Override
    public PageResult<ScheduleJobVO> listJobs(Integer pageNum, Integer pageSize, String jobGroup, String status) {
        Page<ScheduleJob> page = new Page<ScheduleJob>(pageNum, pageSize);
        LambdaQueryWrapper<ScheduleJob> wrapper = new LambdaQueryWrapper<ScheduleJob>();
        if (StringUtils.hasText(jobGroup)) {
            wrapper.eq(ScheduleJob::getJobGroup, jobGroup);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ScheduleJob::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(ScheduleJob::getCreateTime);

        Page<ScheduleJob> result = scheduleJobMapper.selectPage(page, wrapper);
        List<ScheduleJobVO> voList = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        requireJob(id);
        scheduleService.removeJob(id);
        scheduleJobMapper.deleteById(id);
        log.info("删除调度任务: id={}", id);
    }

    @Override
    @Transactional
    public void pauseJob(Long id) {
        ScheduleJob job = requireJob(id);
        if (JobStatus.PAUSED.getCode().equals(job.getStatus())) {
            return;
        }
        job.setStatus(JobStatus.PAUSED.getCode());
        scheduleJobMapper.updateById(job);
        scheduleService.removeJob(id);
        log.info("暂停调度任务: id={}", id);
    }

    @Override
    @Transactional
    public void resumeJob(Long id) {
        ScheduleJob job = requireJob(id);
        String cron = validateCron(job.getCronExpression());
        // 库里可能是坏 cron 或已下线的处理器（老数据），恢复时必须重新校验一遍再登记，
        // 否则就是"状态改成 RUNNING + 一个永远不会成功的触发器"
        job.setJobClass(validateJobClass(job.getJobClass()));
        job.setStatus(JobStatus.RUNNING.getCode());
        scheduleJobMapper.updateById(job);
        scheduleService.registerJob(job.getId(), cron, taskOf(job));
        log.info("恢复调度任务: id={}, cron={}", id, cron);
    }

    @Override
    public void runOnce(Long id) {
        ScheduleJob job = requireJob(id);
        scheduleService.triggerOnce(id, taskOf(job));
        log.info("立即执行调度任务: id={}", id);
    }

    @Override
    public PageResult<ScheduleJobLogVO> getLogs(Long jobId, Integer pageNum, Integer pageSize) {
        requireJob(jobId);
        Page<ScheduleJobLog> page = new Page<ScheduleJobLog>(pageNum, pageSize);
        LambdaQueryWrapper<ScheduleJobLog> wrapper = new LambdaQueryWrapper<ScheduleJobLog>();
        wrapper.eq(ScheduleJobLog::getJobId, jobId)
               .orderByDesc(ScheduleJobLog::getCreateTime);

        Page<ScheduleJobLog> result = scheduleJobLogMapper.selectPage(page, wrapper);
        List<ScheduleJobLogVO> voList = result.getRecords().stream()
                .map(logEntry -> {
                    ScheduleJobLogVO vo = new ScheduleJobLogVO();
                    BeanUtils.copyProperties(logEntry, vo);
                    return vo;
                }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 进程就绪后把库里的 {@code RUNNING} 行重新投影成触发器。
     *
     * <p>用 {@code ApplicationReadyEvent} 而不是 {@code @PostConstruct}：装载要读库，
     * 而 {@code @PostConstruct} 时数据源与连接池未必可用，症状是"启动即失败但服务照常起来"。</p>
     *
     * <p>读不到处理器的行置为 {@code ERROR}：这类行原本会因为登记失败而只打一条 error 日志，
     * 页面却显示"运行中"。改成异常之后，管理端的异常计数才有非零的意义。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void armRunningJobs() {
        List<ScheduleJob> running = scheduleJobMapper.selectList(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getStatus, JobStatus.RUNNING.getCode()));
        int armed = 0;
        int broken = 0;
        for (ScheduleJob job : running) {
            if (jobRegistry.contains(job.getJobClass())) {
                try {
                    scheduleService.registerJob(job.getId(), job.getCronExpression(), taskOf(job));
                    armed++;
                    continue;
                } catch (RuntimeException e) {
                    log.warn("启动装载失败: jobId={}, reason={}", job.getId(), e.getMessage());
                }
            } else {
                log.warn("启动装载跳过: jobId={}, jobClass={} 未注册处理器", job.getId(), job.getJobClass());
            }
            job.setStatus(JobStatus.ERROR.getCode());
            scheduleJobMapper.updateById(job);
            broken++;
        }
        log.info("启动装载调度任务: 运行中 {} 条，登记触发器 {} 个，处理器/cron 不可用置异常 {} 条",
                running.size(), armed, broken);
    }

    /** 任务被触发时实际执行的东西 —— 四个入口共用一份，免得哪天只改了一处 */
    private Runnable taskOf(final ScheduleJob job) {
        return new Runnable() {
            @Override
            public void run() {
                jobExecutionService.executeJob(job.getId(), job.getJobName(),
                        job.getJobClass(), job.getJobParams());
            }
        };
    }

    private String validateJobClass(String jobClass) {
        // require() 抛 400 时会把可选处理器一起带上，用户不用去猜能填什么
        return jobRegistry.require(jobClass).key();
    }

    private String validateCron(String cronExpression) {
        if (!StringUtils.hasText(cronExpression)) {
            throw new BizException("Cron 表达式不能为空");
        }
        // 借 CronExpression 解析一次：不合法就在落库前炸，事务回滚，库里不会有"运行中但没人打"的行
        try {
            org.springframework.scheduling.support.CronExpression.parse(cronExpression.trim());
        } catch (RuntimeException e) {
            throw new BizException("Cron 表达式不合法: " + cronExpression
                    + "（需要 6 段式，如 0 0/30 * * * ?）");
        }
        return cronExpression.trim();
    }

    private ScheduleJob requireJob(Long id) {
        ScheduleJob job = id == null ? null : scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在: " + id);
        }
        return job;
    }

    private ScheduleJobVO toVO(ScheduleJob job) {
        ScheduleJobVO vo = new ScheduleJobVO();
        BeanUtils.copyProperties(job, vo);
        ScheduledJob handler = jobRegistry.find(job.getJobClass());
        vo.setHandlerRegistered(handler != null);
        vo.setHandlerLabel(handler == null ? null : handler.label());
        return vo;
    }

    @Override
    public List<JobHandlerVO> listHandlers() {
        List<JobHandlerVO> list = new ArrayList<JobHandlerVO>();
        for (ScheduledJob handler : jobRegistry.available()) {
            list.add(new JobHandlerVO(handler.key(), handler.label(), handler.description()));
        }
        return list;
    }
}
