package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.monitor.dto.HeartbeatDTO;
import com.dataviz.monitor.dto.ServiceRegisterDTO;
import com.dataviz.monitor.entity.ServiceInstance;
import com.dataviz.monitor.enums.InstanceStatus;
import com.dataviz.monitor.mapper.ServiceInstanceMapper;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.vo.ServiceInstanceVO;
import com.dataviz.common.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服务注册服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRegistryServiceImpl implements ServiceRegistryService {

    private final ServiceInstanceMapper serviceInstanceMapper;

    @Override
    @Transactional
    public ServiceInstanceVO register(ServiceRegisterDTO registerDTO) {
        // 检查实例是否已存在
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, registerDTO.getServiceName())
               .eq(ServiceInstance::getInstanceId, registerDTO.getInstanceId());
        ServiceInstance existing = serviceInstanceMapper.selectOne(wrapper);

        ServiceInstance instance;
        if (existing != null) {
            // 更新已存在的实例
            existing.setHost(registerDTO.getHost());
            existing.setPort(registerDTO.getPort());
            existing.setStatus(InstanceStatus.UP.getCode());
            existing.setLastHeartbeat(LocalDateTime.now());
            existing.setUpdateTime(LocalDateTime.now());
            if (registerDTO.getMetadata() != null) {
                existing.setMetadata(JsonUtils.toJson(registerDTO.getMetadata()));
            }
            serviceInstanceMapper.updateById(existing);
            instance = existing;
            log.info("更新服务实例注册: serviceName={}, instanceId={}", registerDTO.getServiceName(), registerDTO.getInstanceId());
        } else {
            // 新建实例
            instance = new ServiceInstance();
            BeanUtils.copyProperties(registerDTO, instance);
            instance.setStatus(InstanceStatus.UP.getCode());
            instance.setLastHeartbeat(LocalDateTime.now());
            if (registerDTO.getMetadata() != null) {
                instance.setMetadata(JsonUtils.toJson(registerDTO.getMetadata()));
            }
            serviceInstanceMapper.insert(instance);
            log.info("注册服务实例: serviceName={}, instanceId={}", registerDTO.getServiceName(), registerDTO.getInstanceId());
        }

        ServiceInstanceVO vo = new ServiceInstanceVO();
        BeanUtils.copyProperties(instance, vo);
        return vo;
    }

    @Override
    @Transactional
    public void deregister(String serviceName, String instanceId) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, serviceName)
               .eq(ServiceInstance::getInstanceId, instanceId);
        int deleted = serviceInstanceMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BizException("服务实例不存在");
        }
        log.info("注销服务实例: serviceName={}, instanceId={}", serviceName, instanceId);
    }

    @Override
    @Transactional
    public void heartbeat(HeartbeatDTO heartbeatDTO) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, heartbeatDTO.getServiceName())
               .eq(ServiceInstance::getInstanceId, heartbeatDTO.getInstanceId());
        ServiceInstance instance = serviceInstanceMapper.selectOne(wrapper);

        if (instance == null) {
            log.warn("收到未知实例心跳: serviceName={}, instanceId={}", heartbeatDTO.getServiceName(), heartbeatDTO.getInstanceId());
            return;
        }

        instance.setLastHeartbeat(LocalDateTime.now());
        if (heartbeatDTO.getStatus() != null) {
            instance.setStatus(heartbeatDTO.getStatus());
        }
        instance.setUpdateTime(LocalDateTime.now());
        serviceInstanceMapper.updateById(instance);
    }

    @Override
    public List<ServiceInstanceVO> getAllInstances() {
        List<ServiceInstance> instances = serviceInstanceMapper.selectList(
                new LambdaQueryWrapper<ServiceInstance>().orderByDesc(ServiceInstance::getLastHeartbeat));
        return instances.stream().map(inst -> {
            ServiceInstanceVO vo = new ServiceInstanceVO();
            BeanUtils.copyProperties(inst, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ServiceInstanceVO> getInstanceList(String serviceName) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceInstance::getServiceName, serviceName)
               .orderByDesc(ServiceInstance::getLastHeartbeat);
        List<ServiceInstance> instances = serviceInstanceMapper.selectList(wrapper);
        return instances.stream().map(inst -> {
            ServiceInstanceVO vo = new ServiceInstanceVO();
            BeanUtils.copyProperties(inst, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void detectDeadInstances(long timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(ServiceInstance::getLastHeartbeat, threshold)
               .ne(ServiceInstance::getStatus, InstanceStatus.DOWN.getCode());
        List<ServiceInstance> deadInstances = serviceInstanceMapper.selectList(wrapper);

        for (ServiceInstance instance : deadInstances) {
            instance.setStatus(InstanceStatus.DOWN.getCode());
            instance.setUpdateTime(LocalDateTime.now());
            serviceInstanceMapper.updateById(instance);
            log.warn("检测到死亡实例: serviceName={}, instanceId={}, lastHeartbeat={}",
                    instance.getServiceName(), instance.getInstanceId(), instance.getLastHeartbeat());
        }
    }
}
