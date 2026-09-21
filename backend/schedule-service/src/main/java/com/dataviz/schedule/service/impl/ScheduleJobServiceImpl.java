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
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.mapper.ScheduleJobMapper;
import com.dataviz.schedule.service.JobExecutionService;
import com.dataviz.schedule.service.ScheduleJobService;
import com.dataviz.schedule.service.ScheduleService;
import com.dataviz.schedule.vo.ScheduleJobLogVO;
import com.dataviz.schedule.vo.ScheduleJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调度任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleJobServiceImpl implements ScheduleJobService {

    private final ScheduleJobMapper scheduleJobMapper;
    private final ScheduleJobLogMapper scheduleJobLogMapper;
    private final ScheduleService scheduleService;
    private final JobExecutionService jobExecutionService;

    @Override
    @Transactional
    public Long createJob(ScheduleJobCreateDTO createDTO) {
        ScheduleJob job = new ScheduleJob();
        BeanUtils.copyProperties(createDTO, job);
        job.setStatus(JobStatus.RUNNING.getCode());
        if (job.getMisfirePolicy() == null) {
            job.setMisfirePolicy("IGNORE");
        }
        scheduleJobMapper.insert(job);
        log.info("创建调度任务: id={}, name={}", job.getId(), job.getJobName());

        // 注册到调度器
        scheduleService.registerJob(job.getId(), job.getCronExpression(),
                () -> jobExecutionService.executeJob(job.getId(), job.getJobName(), job.getJobClass(), job.getJobParams()));

        return job.getId();
    }

    @Override
    @Transactional
    public void updateJob(ScheduleJobUpdateDTO updateDTO) {
        ScheduleJob job = scheduleJobMapper.selectById(updateDTO.getId());
        if (job == null) {
            throw new BizException("任务不存在");
        }
        BeanUtils.copyProperties(updateDTO, job);
        scheduleJobMapper.updateById(job);
        log.info("更新调度任务: id={}", job.getId());

        // 重新注册到调度器
        scheduleService.removeJob(job.getId());
        scheduleService.registerJob(job.getId(), job.getCronExpression(),
                () -> jobExecutionService.executeJob(job.getId(), job.getJobName(), job.getJobClass(), job.getJobParams()));
    }

    @Override
    public ScheduleJobVO getJobById(Long id) {
        ScheduleJob job = scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        ScheduleJobVO vo = new ScheduleJobVO();
        BeanUtils.copyProperties(job, vo);
        return vo;
    }

    @Override
    public PageResult<ScheduleJobVO> listJobs(Integer pageNum, Integer pageSize, String jobGroup, String status) {
        Page<ScheduleJob> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ScheduleJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(jobGroup)) {
            wrapper.eq(ScheduleJob::getJobGroup, jobGroup);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ScheduleJob::getStatus, status);
        }
        wrapper.orderByDesc(ScheduleJob::getCreateTime);

        Page<ScheduleJob> result = scheduleJobMapper.selectPage(page, wrapper);
        List<ScheduleJobVO> voList = result.getRecords().stream().map(job -> {
            ScheduleJobVO vo = new ScheduleJobVO();
            BeanUtils.copyProperties(job, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        ScheduleJob job = scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        // 从调度器移除
        scheduleService.removeJob(id);
        scheduleJobMapper.deleteById(id);
        log.info("删除调度任务: id={}", id);
    }

    @Override
    @Transactional
    public void pauseJob(Long id) {
        ScheduleJob job = scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        job.setStatus(JobStatus.PAUSED.getCode());
        scheduleJobMapper.updateById(job);
        scheduleService.pauseJob(id);
        log.info("暂停调度任务: id={}", id);
    }

    @Override
    @Transactional
    public void resumeJob(Long id) {
        ScheduleJob job = scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        job.setStatus(JobStatus.RUNNING.getCode());
        scheduleJobMapper.updateById(job);
        scheduleService.resumeJob(id);
        log.info("恢复调度任务: id={}", id);
    }

    @Override
    public void runOnce(Long id) {
        ScheduleJob job = scheduleJobMapper.selectById(id);
        if (job == null) {
            throw new BizException("任务不存在");
        }
        scheduleService.triggerOnce(id,
                () -> jobExecutionService.executeJob(job.getId(), job.getJobName(), job.getJobClass(), job.getJobParams()));
        log.info("立即执行调度任务: id={}", id);
    }

    @Override
    public PageResult<ScheduleJobLogVO> getLogs(Long jobId, Integer pageNum, Integer pageSize) {
        Page<ScheduleJobLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ScheduleJobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleJobLog::getJobId, jobId)
               .orderByDesc(ScheduleJobLog::getCreateTime);

        Page<ScheduleJobLog> result = scheduleJobLogMapper.selectPage(page, wrapper);
        List<ScheduleJobLogVO> voList = result.getRecords().stream().map(logEntry -> {
            ScheduleJobLogVO vo = new ScheduleJobLogVO();
            BeanUtils.copyProperties(logEntry, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageNum, pageSize);
    }
}
