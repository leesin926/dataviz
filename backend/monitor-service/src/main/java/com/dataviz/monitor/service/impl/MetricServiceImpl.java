package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.util.JsonUtils;
import com.dataviz.monitor.dto.MetricReportDTO;
import com.dataviz.monitor.entity.MonitorMetric;
import com.dataviz.monitor.mapper.MonitorMetricMapper;
import com.dataviz.monitor.service.MetricService;
import com.dataviz.monitor.vo.MetricHistoryVO;
import com.dataviz.monitor.vo.MetricVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指标服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MonitorMetricMapper monitorMetricMapper;

    @Override
    @Transactional
    public void reportMetrics(MetricReportDTO reportDTO) {
        MonitorMetric metric = new MonitorMetric();
        metric.setServiceName(reportDTO.getServiceName());
        metric.setInstanceId(reportDTO.getInstanceId());
        metric.setMetricName(reportDTO.getMetricName());
        metric.setMetricValue(reportDTO.getMetricValue());
        metric.setMetricType(reportDTO.getMetricType());
        if (reportDTO.getTags() != null) {
            metric.setTags(JsonUtils.toJson(reportDTO.getTags()));
        }
        metric.setTimestamp(reportDTO.getTimestamp() != null ? reportDTO.getTimestamp() : LocalDateTime.now());
        monitorMetricMapper.insert(metric);
    }

    @Override
    @Transactional
    public void reportMetricsBatch(List<MetricReportDTO> reportDTOs) {
        for (MetricReportDTO dto : reportDTOs) {
            reportMetrics(dto);
        }
    }

    @Override
    public List<MetricVO> getMetrics(String serviceName, String instanceId) {
        LambdaQueryWrapper<MonitorMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorMetric::getServiceName, serviceName);
        if (instanceId != null && !instanceId.isEmpty()) {
            wrapper.eq(MonitorMetric::getInstanceId, instanceId);
        }
        wrapper.orderByDesc(MonitorMetric::getTimestamp);
        List<MonitorMetric> metrics = monitorMetricMapper.selectList(wrapper);

        // 这条端点的语义是"当前指标"，所以每个 (实例, 指标名) 只留最新一条。
        // 直接返回查询结果会把整张历史表倒序吐给调用方，监控页拿到的不是现值而是几个月的点。
        Map<String, MonitorMetric> latestPerMetric = new LinkedHashMap<>();
        for (MonitorMetric m : metrics) {
            String key = m.getInstanceId() + "|" + m.getMetricName();
            if (!latestPerMetric.containsKey(key)) {
                latestPerMetric.put(key, m);
            }
        }

        return latestPerMetric.values().stream().map(this::toVO).collect(Collectors.toList());
    }

    private MetricVO toVO(MonitorMetric m) {
        MetricVO vo = new MetricVO();
        vo.setId(m.getId());
        vo.setServiceName(m.getServiceName());
        vo.setInstanceId(m.getInstanceId());
        vo.setMetricName(m.getMetricName());
        vo.setMetricValue(m.getMetricValue());
        vo.setMetricType(m.getMetricType());
        if (m.getTags() != null && !m.getTags().isEmpty()) {
            vo.setTags(JsonUtils.fromJson(m.getTags(), new TypeReference<Map<String, String>>() {}));
        }
        vo.setTimestamp(m.getTimestamp());
        return vo;
    }

    @Override
    public List<MetricHistoryVO> getMetricHistory(String serviceName, String metricName,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<MonitorMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorMetric::getServiceName, serviceName)
               .eq(MonitorMetric::getMetricName, metricName)
               .ge(startTime != null, MonitorMetric::getTimestamp, startTime)
               .le(endTime != null, MonitorMetric::getTimestamp, endTime)
               .orderByAsc(MonitorMetric::getTimestamp);
        List<MonitorMetric> metrics = monitorMetricMapper.selectList(wrapper);

        return metrics.stream().map(m -> MetricHistoryVO.builder()
                .metricName(m.getMetricName())
                .metricValue(m.getMetricValue())
                .metricType(m.getMetricType())
                .timestamp(m.getTimestamp())
                .build()).collect(Collectors.toList());
    }
}
