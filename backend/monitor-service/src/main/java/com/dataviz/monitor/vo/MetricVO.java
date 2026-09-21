package com.dataviz.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 指标VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricVO {

    private Long id;

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
     * 指标类型
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
