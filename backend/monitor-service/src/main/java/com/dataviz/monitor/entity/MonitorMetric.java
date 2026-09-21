package com.dataviz.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控指标实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("monitor_metric")
public class MonitorMetric {

    @TableId(type = IdType.AUTO)
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
     * 指标类型: GAUGE/COUNTER/HISTOGRAM
     */
    private String metricType;

    /**
     * 标签（JSON格式）
     */
    private String tags;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
