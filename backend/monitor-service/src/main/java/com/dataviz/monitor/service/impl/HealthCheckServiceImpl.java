package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.monitor.entity.MonitorAlert;
import com.dataviz.monitor.entity.ServiceInstance;
import com.dataviz.monitor.enums.AlertStatus;
import com.dataviz.monitor.enums.InstanceStatus;
import com.dataviz.monitor.mapper.MonitorAlertMapper;
import com.dataviz.monitor.mapper.ServiceInstanceMapper;
import com.dataviz.monitor.service.HealthCheckService;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.vo.ServiceHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 健康检查服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckServiceImpl implements HealthCheckService {

    private final ServiceInstanceMapper serviceInstanceMapper;
    private final MonitorAlertMapper monitorAlertMapper;
    private final ServiceRegistryService serviceRegistryService;

    @Override
    public ServiceHealthVO checkHealth(String serviceName) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, serviceName);
        List<ServiceInstance> instances = serviceInstanceMapper.selectList(wrapper);

        int instanceCount = instances.size();
        int healthyCount = (int) instances.stream()
                .filter(i -> InstanceStatus.UP.getCode().equals(i.getStatus()))
                .count();

        // 计算整体状态
        String overallStatus;
        if (healthyCount == 0) {
            overallStatus = "DOWN";
        } else if (healthyCount == instanceCount) {
            overallStatus = "UP";
        } else {
            overallStatus = "PARTIAL";
        }

        // 查找最近心跳时间
        LocalDateTime lastHeartbeat = instances.stream()
                .map(ServiceInstance::getLastHeartbeat)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        // 查询活跃告警数
        Long activeAlertCount = monitorAlertMapper.selectCount(
                new LambdaQueryWrapper<MonitorAlert>()
                        .eq(MonitorAlert::getServiceName, serviceName)
                        .eq(MonitorAlert::getStatus, AlertStatus.ACTIVE.getCode()));

        return ServiceHealthVO.builder()
                .serviceName(serviceName)
                .instanceCount(instanceCount)
                .healthyCount(healthyCount)
                .overallStatus(overallStatus)
                .lastCheckTime(LocalDateTime.now())
                .lastHeartbeat(lastHeartbeat)
                .activeAlertCount(activeAlertCount.intValue())
                .build();
    }

    @Override
    @Transactional
    public void updateHealth(String serviceName, String instanceId, String status) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, serviceName)
               .eq(ServiceInstance::getInstanceId, instanceId);
        ServiceInstance instance = serviceInstanceMapper.selectOne(wrapper);
        if (instance != null) {
            instance.setStatus(status);
            instance.setUpdateTime(LocalDateTime.now());
            serviceInstanceMapper.updateById(instance);

            // 如果状态变为DOWN，创建告警
            if (InstanceStatus.DOWN.getCode().equals(status)) {
                createAlert(serviceName, instanceId);
            }
        }
    }

    @Override
    @Scheduled(fixedRate = 30000) // 每30秒执行一次
    public void checkAllServices() {
        // 检测死亡实例（超过90秒无心跳视为死亡）
        serviceRegistryService.detectDeadInstances(90);

        // 查询所有不同的服务名称
        List<ServiceInstance> allInstances = serviceInstanceMapper.selectList(null);
        List<String> serviceNames = allInstances.stream()
                .map(ServiceInstance::getServiceName)
                .distinct()
                .collect(Collectors.toList());

        for (String serviceName : serviceNames) {
            ServiceHealthVO health = checkHealth(serviceName);
            if ("DOWN".equals(health.getOverallStatus())) {
                log.warn("服务健康检查告警: serviceName={}, status=DOWN, instances={}/{}",
                        serviceName, health.getHealthyCount(), health.getInstanceCount());
            }
        }
    }

    /**
     * 创建服务宕机告警
     */
    private void createAlert(String serviceName, String instanceId) {
        // 检查是否已有活跃告警
        Long existingCount = monitorAlertMapper.selectCount(
                new LambdaQueryWrapper<MonitorAlert>()
                        .eq(MonitorAlert::getServiceName, serviceName)
                        .eq(MonitorAlert::getStatus, AlertStatus.ACTIVE.getCode())
                        .eq(MonitorAlert::getMetricName, "instance_down"));
        if (existingCount > 0) {
            return;
        }

        MonitorAlert alert = MonitorAlert.builder()
                .serviceName(serviceName)
                .metricName("instance_down")
                .condition("==")
                .message("服务实例宕机: " + serviceName + " - " + instanceId)
                .status(AlertStatus.ACTIVE.getCode())
                .createTime(LocalDateTime.now())
                .build();
        monitorAlertMapper.insert(alert);
        log.warn("创建服务宕机告警: serviceName={}, instanceId={}", serviceName, instanceId);
    }
}
