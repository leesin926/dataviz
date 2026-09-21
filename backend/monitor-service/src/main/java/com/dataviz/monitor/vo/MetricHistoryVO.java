package com.dataviz.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标历史VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricHistoryVO {

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
     * 时间戳
     */
    private LocalDateTime timestamp;
}
