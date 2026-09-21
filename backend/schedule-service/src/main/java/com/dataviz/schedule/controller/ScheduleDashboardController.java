package com.dataviz.schedule.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.result.R;
import com.dataviz.schedule.entity.ScheduleJob;
import com.dataviz.schedule.entity.ScheduleJobLog;
import com.dataviz.schedule.enums.JobStatus;
import com.dataviz.schedule.mapper.ScheduleJobLogMapper;
import com.dataviz.schedule.mapper.ScheduleJobMapper;
import com.dataviz.schedule.vo.ScheduleJobVO;
import com.dataviz.schedule.vo.ScheduleStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 调度仪表盘控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/schedule/dashboard")
@RequiredArgsConstructor
public class ScheduleDashboardController {

    private final ScheduleJobMapper scheduleJobMapper;
    private final ScheduleJobLogMapper scheduleJobLogMapper;

    /**
     * 获取任务统计
     */
    @GetMapping("/stats")
    public R<ScheduleStatsVO> getJobStats() {
        // 查询各状态任务数
        Long totalJobs = scheduleJobMapper.selectCount(null);
        Long runningJobs = scheduleJobMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJob>().eq(ScheduleJob::getStatus, JobStatus.RUNNING.getCode()));
        Long pausedJobs = scheduleJobMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJob>().eq(ScheduleJob::getStatus, JobStatus.PAUSED.getCode()));
        Long errorJobs = scheduleJobMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJob>().eq(ScheduleJob::getStatus, JobStatus.ERROR.getCode()));

        // 今日执行统计
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        Long todayExecutions = scheduleJobLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJobLog>().ge(ScheduleJobLog::getCreateTime, todayStart));
        Long todaySuccess = scheduleJobLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJobLog>()
                        .ge(ScheduleJobLog::getCreateTime, todayStart)
                        .eq(ScheduleJobLog::getStatus, "SUCCESS"));
        Long todayFailed = scheduleJobLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleJobLog>()
                        .ge(ScheduleJobLog::getCreateTime, todayStart)
                        .eq(ScheduleJobLog::getStatus, "FAILED"));

        ScheduleStatsVO stats = ScheduleStatsVO.builder()
                .totalJobs(totalJobs.intValue())
                .runningJobs(runningJobs.intValue())
                .pausedJobs(pausedJobs.intValue())
                .errorJobs(errorJobs.intValue())
                .todayExecutions(todayExecutions)
                .todaySuccess(todaySuccess)
                .todayFailed(todayFailed)
                .build();
        return R.ok(stats);
    }

    /**
     * 获取运行中的任务列表
     */
    @GetMapping("/runningJobs")
    public R<List<ScheduleJobVO>> getRunningJobs() {
        LambdaQueryWrapper<ScheduleJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleJob::getStatus, JobStatus.RUNNING.getCode())
               .orderByDesc(ScheduleJob::getCreateTime);
        List<ScheduleJob> jobs = scheduleJobMapper.selectList(wrapper);
        List<ScheduleJobVO> voList = jobs.stream().map(job -> {
            ScheduleJobVO vo = new ScheduleJobVO();
            BeanUtils.copyProperties(job, vo);
            return vo;
        }).collect(Collectors.toList());
        return R.ok(voList);
    }
}
