package com.dataviz.etl.service;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.etl.dto.EtlTaskCreateDTO;
import com.dataviz.etl.dto.EtlTaskUpdateDTO;
import com.dataviz.etl.vo.EtlMetricsVO;
import com.dataviz.etl.vo.EtlTaskLogVO;
import com.dataviz.etl.vo.EtlTaskVO;

import java.util.List;

/**
 * ETL任务服务接口
 */
public interface EtlTaskService {

    /**
     * 创建ETL任务
     */
    Long createTask(EtlTaskCreateDTO dto, String tenantId);

    /**
     * 更新ETL任务
     */
    void updateTask(Long id, EtlTaskUpdateDTO dto);

    /**
     * 删除ETL任务
     */
    void deleteTask(Long id);

    /**
     * 获取任务详情
     */
    EtlTaskVO getTaskById(Long id);

    /**
     * 分页查询任务列表
     */
    PageResult<EtlTaskVO> listTasks(String name, String status, String tenantId, PageQuery pageQuery);

    /**
     * 启动任务
     */
    void startTask(Long id);

    /**
     * 停止任务
     */
    void stopTask(Long id);

    /**
     * 暂停任务
     */
    void pauseTask(Long id);

    /**
     * 获取任务日志
     */
    List<EtlTaskLogVO> getTaskLogs(Long taskId);

    /**
     * 获取任务指标
     */
    EtlMetricsVO getTaskMetrics(Long taskId);
}
