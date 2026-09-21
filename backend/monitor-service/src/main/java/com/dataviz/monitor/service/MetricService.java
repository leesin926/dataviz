package com.dataviz.monitor.service;

import com.dataviz.monitor.dto.MetricReportDTO;
import com.dataviz.monitor.vo.MetricHistoryVO;
import com.dataviz.monitor.vo.MetricVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标服务接口
 */
public interface MetricService {

    /**
     * 上报指标
     */
    void reportMetrics(MetricReportDTO reportDTO);

    /**
     * 批量上报指标
     */
    void reportMetricsBatch(List<MetricReportDTO> reportDTOs);

    /**
     * 获取服务当前指标
     */
    List<MetricVO> getMetrics(String serviceName, String instanceId);

    /**
     * 获取指标历史数据（带时间范围）
     */
    List<MetricHistoryVO> getMetricHistory(String serviceName, String metricName,
                                            LocalDateTime startTime, LocalDateTime endTime);
}
