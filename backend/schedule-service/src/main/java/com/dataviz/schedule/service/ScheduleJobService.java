package com.dataviz.schedule.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.schedule.dto.ScheduleJobCreateDTO;
import com.dataviz.schedule.dto.ScheduleJobUpdateDTO;
import com.dataviz.schedule.vo.ScheduleJobLogVO;
import com.dataviz.schedule.vo.ScheduleJobVO;

/**
 * 调度任务服务接口
 */
public interface ScheduleJobService {

    /**
     * 创建任务
     */
    Long createJob(ScheduleJobCreateDTO createDTO);

    /**
     * 更新任务
     */
    void updateJob(ScheduleJobUpdateDTO updateDTO);

    /**
     * 获取任务详情
     */
    ScheduleJobVO getJobById(Long id);

    /**
     * 分页查询任务列表
     */
    PageResult<ScheduleJobVO> listJobs(Integer pageNum, Integer pageSize, String jobGroup, String status);

    /**
     * 删除任务
     */
    void deleteJob(Long id);

    /**
     * 暂停任务
     */
    void pauseJob(Long id);

    /**
     * 恢复任务
     */
    void resumeJob(Long id);

    /**
     * 立即执行一次任务
     */
    void runOnce(Long id);

    /**
     * 查询任务执行日志
     */
    PageResult<ScheduleJobLogVO> getLogs(Long jobId, Integer pageNum, Integer pageSize);
}
