package com.dataviz.monitor.dto;

import lombok.Data;

/**
 * 心跳上报DTO
 */
@Data
public class HeartbeatDTO {

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 实例状态: UP/DOWN/STARTING
     */
    private String status;
}
