package com.dataviz.monitor.service;

import com.dataviz.monitor.dto.HeartbeatDTO;
import com.dataviz.monitor.dto.ServiceRegisterDTO;
import com.dataviz.monitor.vo.ServiceInstanceVO;

import java.util.List;

/**
 * 服务注册服务接口
 */
public interface ServiceRegistryService {

    /**
     * 注册服务实例
     */
    ServiceInstanceVO register(ServiceRegisterDTO registerDTO);

    /**
     * 注销服务实例
     */
    void deregister(String serviceName, String instanceId);

    /**
     * 处理心跳上报
     */
    void heartbeat(HeartbeatDTO heartbeatDTO);

    /**
     * 获取所有实例
     */
    List<ServiceInstanceVO> getAllInstances();

    /**
     * 获取服务下所有实例
     */
    List<ServiceInstanceVO> getInstanceList(String serviceName);

    /**
     * 检测并标记死亡实例
     */
    void detectDeadInstances(long timeoutSeconds);
}
