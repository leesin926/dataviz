package com.dataviz.monitor.service;

import com.dataviz.monitor.vo.ServiceHealthVO;

/**
 * 健康检查服务接口
 */
public interface HealthCheckService {

    /**
     * 检查单个服务健康状态
     */
    ServiceHealthVO checkHealth(String serviceName);

    /**
     * 更新服务健康状态
     */
    void updateHealth(String serviceName, String instanceId, String status);

    /**
     * 定时检查所有注册服务
     */
    void checkAllServices();
}
