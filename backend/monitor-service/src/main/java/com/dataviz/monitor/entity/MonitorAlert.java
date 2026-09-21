package com.dataviz.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控告警实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("monitor_alert")
public class MonitorAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 指标名称
     */
    private String metricName;

    /**
     * 告警条件表达式（如 >, <, =）
     */
    @TableField("`condition`")
    private String condition;

    /**
     * 阈值
     */
    private BigDecimal threshold;

    /**
     * 告警消息
     */
    private String message;

    /**
     * 告警状态: ACTIVE/RESOLVED
     */
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 解决时间
     */
    private LocalDateTime resolvedTime;
}
