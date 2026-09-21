package com.dataviz.monitor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务健康状态VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthVO {

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 实例数量
     */
    private Integer instanceCount;

    /**
     * 健康实例数量
     */
    private Integer healthyCount;

    /**
     * 整体状态: UP/DOWN/PARTIAL
     */
    private String overallStatus;

    /**
     * 最后检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次心跳时间
     */
    private LocalDateTime lastHeartbeat;

    /**
     * 活跃告警数
     */
    private Integer activeAlertCount;
}
