package com.dataviz.model.service;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.MetricCreateDTO;
import com.dataviz.model.vo.MetricVO;

/**
 * 指标服务接口
 */
public interface MetricService {

    /**
     * 创建指标
     */
    Long createMetric(MetricCreateDTO dto, String tenantId);

    /**
     * 更新指标
     */
    void updateMetric(Long id, MetricCreateDTO dto);

    /**
     * 删除指标
     */
    void deleteMetric(Long id);

    /**
     * 获取指标详情
     */
    MetricVO getMetricById(Long id);

    /**
     * 分页查询指标列表
     */
    PageResult<MetricVO> listMetrics(Long datasourceId, String tenantId, PageQuery pageQuery);
}
