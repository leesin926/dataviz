package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.monitor.entity.MonitorAlert;
import com.dataviz.monitor.entity.ServiceInstance;
import com.dataviz.monitor.mapper.MonitorAlertMapper;
import com.dataviz.monitor.mapper.ServiceInstanceMapper;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.test.MybatisPlusTableInfoHelper;
import com.dataviz.monitor.vo.ServiceHealthVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 健康聚合与宕机告警。三档整体状态（全好/全坏/半好）和"同一实例不重复开告警"
 * 是监控页能不能看的关键：少了 PARTIAL，滚动发布期间整服务会被判成 DOWN。
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceImplTest {

    @Mock
    private ServiceInstanceMapper serviceInstanceMapper;

    @Mock
    private MonitorAlertMapper monitorAlertMapper;

    @Mock
    private ServiceRegistryService serviceRegistryService;

    @InjectMocks
    private HealthCheckServiceImpl healthCheckService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(ServiceInstance.class, MonitorAlert.class);
    }

    @Test
    @DisplayName("全部实例在线 - 整体 UP，最近心跳取最大且忽略空值")
    void should_reportUp_when_allInstancesHealthy() {
        LocalDateTime newer = LocalDateTime.now();
        LocalDateTime older = newer.minusMinutes(3);
        when(serviceInstanceMapper.selectList(anyWrapper())).thenReturn(Arrays.asList(
                instance("al-1", "UP", newer),
                instance("al-2", "UP", null),
                instance("al-3", "UP", older)));
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(0L);

        ServiceHealthVO health = healthCheckService.checkHealth("alert-service");

        assertEquals("UP", health.getOverallStatus());
        assertEquals(Integer.valueOf(3), health.getInstanceCount());
        assertEquals(Integer.valueOf(3), health.getHealthyCount());
        assertEquals(newer, health.getLastHeartbeat());
        assertEquals(0, health.getActiveAlertCount());
    }

    @Test
    @DisplayName("一个都不在线 - 整体 DOWN")
    void should_reportDown_when_noInstanceUp() {
        when(serviceInstanceMapper.selectList(anyWrapper())).thenReturn(Arrays.asList(
                instance("al-1", "DOWN", LocalDateTime.now()),
                instance("al-2", "STARTING", LocalDateTime.now())));
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(2L);

        ServiceHealthVO health = healthCheckService.checkHealth("alert-service");

        assertEquals("DOWN", health.getOverallStatus());
        assertEquals(Integer.valueOf(0), health.getHealthyCount());
        assertEquals(2, health.getActiveAlertCount());
    }

    @Test
    @DisplayName("部分在线 - 整体 PARTIAL，不能误判成 DOWN")
    void should_reportPartial_when_someInstancesUp() {
        when(serviceInstanceMapper.selectList(anyWrapper())).thenReturn(Arrays.asList(
                instance("al-1", "UP", LocalDateTime.now()),
                instance("al-2", "DOWN", LocalDateTime.now())));
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(1L);

        ServiceHealthVO health = healthCheckService.checkHealth("alert-service");

        assertEquals("PARTIAL", health.getOverallStatus());
        assertEquals(Integer.valueOf(1), health.getHealthyCount());
    }

    @Test
    @DisplayName("查不到任何实例 - 计 0、心跳为空，但整体仍按 DOWN 报")
    void should_reportDown_when_noInstancesAtAll() {
        when(serviceInstanceMapper.selectList(anyWrapper()))
                .thenReturn(Collections.<ServiceInstance>emptyList());
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(0L);

        ServiceHealthVO health = healthCheckService.checkHealth("ghost-service");

        assertEquals("DOWN", health.getOverallStatus());
        assertEquals(Integer.valueOf(0), health.getInstanceCount());
        assertNull(health.getLastHeartbeat());
    }

    @Test
    @DisplayName("实例转 DOWN - 开一条 instance_down 告警")
    void should_createAlert_when_instanceGoesDown() {
        ServiceInstance row = instance("al-1", "UP", LocalDateTime.now());
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(row);
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(0L);

        healthCheckService.updateHealth("alert-service", "al-1", "DOWN");

        assertEquals("DOWN", row.getStatus());
        verify(serviceInstanceMapper).updateById(row);
        ArgumentCaptor<MonitorAlert> captor = ArgumentCaptor.forClass(MonitorAlert.class);
        verify(monitorAlertMapper).insert(captor.capture());
        MonitorAlert alert = captor.getValue();
        assertEquals("instance_down", alert.getMetricName());
        assertEquals("ACTIVE", alert.getStatus());
        assertEquals("alert-service", alert.getServiceName());
        assertEquals("==", alert.getCondition());
        org.junit.jupiter.api.Assertions.assertTrue(
                alert.getMessage().contains("al-1"), alert.getMessage());
    }

    @Test
    @DisplayName("已有同类活跃告警 - 不重复开单")
    void should_skipAlert_when_sameDownAlertAlreadyActive() {
        when(serviceInstanceMapper.selectOne(anyWrapper()))
                .thenReturn(instance("al-1", "UP", LocalDateTime.now()));
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(1L);

        healthCheckService.updateHealth("alert-service", "al-1", "DOWN");

        verify(monitorAlertMapper, never()).insert(any(MonitorAlert.class));
    }

    @Test
    @DisplayName("实例恢复 UP - 只更新状态，不开告警也不消告警")
    void should_notAlert_when_instanceComesUp() {
        when(serviceInstanceMapper.selectOne(anyWrapper()))
                .thenReturn(instance("al-1", "DOWN", LocalDateTime.now()));

        healthCheckService.updateHealth("alert-service", "al-1", "UP");

        verify(monitorAlertMapper, never()).insert(any(MonitorAlert.class));
        verify(monitorAlertMapper, never()).selectCount(anyWrapper());
    }

    @Test
    @DisplayName("未知实例的状态上报 - 无行可改就什么都不做")
    void should_noop_when_instanceUnknown() {
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(null);

        healthCheckService.updateHealth("alert-service", "ghost", "DOWN");

        verify(serviceInstanceMapper, never()).updateById(any(ServiceInstance.class));
        verify(monitorAlertMapper, never()).insert(any(MonitorAlert.class));
    }

    @Test
    @DisplayName("周期体检 - 先做死亡检测，再按去重后的服务名逐个聚合")
    void should_detectThenAggregate_when_scheduledSweep() {
        when(serviceInstanceMapper.selectList(anyWrapper())).thenReturn(Arrays.asList(
                instance("al-1", "UP", LocalDateTime.now()),
                instance("al-2", "UP", LocalDateTime.now())));
        when(monitorAlertMapper.selectCount(anyWrapper())).thenReturn(0L);

        healthCheckService.checkAllServices();

        verify(serviceRegistryService).detectDeadInstances(90);
        // 两个实例同属一个服务，聚合只该跑一次
        verify(monitorAlertMapper).selectCount(anyWrapper());
    }

    @Test
    @DisplayName("没有任何实例 - 周期体检只做死亡检测，不空转聚合")
    void should_onlyDetect_when_noInstancesRegistered() {
        when(serviceInstanceMapper.selectList(anyWrapper()))
                .thenReturn(Collections.<ServiceInstance>emptyList());

        healthCheckService.checkAllServices();

        verify(serviceRegistryService).detectDeadInstances(90);
        verify(monitorAlertMapper, never()).selectCount(anyWrapper());
    }

    private static ServiceInstance instance(String instanceId, String status,
                                            LocalDateTime lastHeartbeat) {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceName("alert-service");
        instance.setInstanceId(instanceId);
        instance.setStatus(status);
        instance.setLastHeartbeat(lastHeartbeat);
        return instance;
    }

    /** 用无类型 any()：{@code selectList(null)} 的实参是 null，{@code any(Class)} 不匹配 null */
    @SuppressWarnings("unchecked")
    private static <T> Wrapper<T> anyWrapper() {
        return (Wrapper<T>) any();
    }
}
