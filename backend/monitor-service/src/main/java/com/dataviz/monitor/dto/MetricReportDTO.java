package com.dataviz.monitor.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 指标上报DTO
 */
@Data
public class MetricReportDTO {

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 指标名称
     */
    private String metricName;

    /**
     * 指标值
     */
    private BigDecimal metricValue;

    /**
     * 指标类型: GAUGE/COUNTER/HISTOGRAM
     */
    private String metricType;

    /**
     * 标签
     */
    private Map<String, String> tags;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
