package com.dataviz.monitor.dto;

import lombok.Data;

import java.util.Map;

/**
 * 服务注册DTO
 */
@Data
public class ServiceRegisterDTO {

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 元数据
     */
    private Map<String, String> metadata;
}
