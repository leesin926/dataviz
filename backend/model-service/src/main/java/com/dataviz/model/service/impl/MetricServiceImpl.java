package com.dataviz.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.MetricCreateDTO;
import com.dataviz.model.entity.ModelMetric;
import com.dataviz.model.mapper.MetricMapper;
import com.dataviz.model.service.MetricService;
import com.dataviz.model.vo.MetricVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 指标服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricMapper metricMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMetric(MetricCreateDTO dto, String tenantId) {
        ModelMetric metric = new ModelMetric();
        BeanUtils.copyProperties(dto, metric);
        metric.setTenantId(tenantId);

        metricMapper.insert(metric);
        log.info("Created metric: {} (id={})", metric.getName(), metric.getId());
        return metric.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMetric(Long id, MetricCreateDTO dto) {
        ModelMetric metric = metricMapper.selectById(id);
        if (metric == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "指标不存在");
        }
        BeanUtils.copyProperties(dto, metric);
        metricMapper.updateById(metric);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetric(Long id) {
        ModelMetric metric = metricMapper.selectById(id);
        if (metric == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "指标不存在");
        }
        metricMapper.deleteById(id);
    }

    @Override
    public MetricVO getMetricById(Long id) {
        ModelMetric metric = metricMapper.selectById(id);
        if (metric == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "指标不存在");
        }
        MetricVO vo = new MetricVO();
        BeanUtils.copyProperties(metric, vo);
        return vo;
    }

    @Override
    public PageResult<MetricVO> listMetrics(Long datasourceId, String tenantId, PageQuery pageQuery) {
        Page<ModelMetric> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<ModelMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelMetric::getTenantId, tenantId);
        if (datasourceId != null) {
            wrapper.eq(ModelMetric::getDatasourceId, datasourceId);
        }
        wrapper.orderByDesc(ModelMetric::getCreateTime);

        Page<ModelMetric> result = metricMapper.selectPage(page, wrapper);
        List<MetricVO> voList = result.getRecords().stream().map(m -> {
            MetricVO vo = new MetricVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }
}
