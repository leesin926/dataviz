package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.monitor.dto.HeartbeatDTO;
import com.dataviz.monitor.dto.ServiceRegisterDTO;
import com.dataviz.monitor.entity.ServiceInstance;
import com.dataviz.monitor.mapper.ServiceInstanceMapper;
import com.dataviz.monitor.test.MybatisPlusTableInfoHelper;
import com.dataviz.monitor.vo.ServiceInstanceVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务注册表：判据落在"写进去的那一行"和"读出来能用的那一份元数据"。
 *
 * <p>{@code metadata} 在库里是 JSON 字符串、在 VO 上是 Map，BeanUtils 会因类型不匹配
 * <b>静默跳过</b>这个字段，所以这里既要钉住序列化入口，也要钉住反序列化兜底：
 * 脏 JSON 不能让监控页整页 500。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServiceRegistryServiceImplTest {

    @Mock
    private ServiceInstanceMapper serviceInstanceMapper;

    @InjectMocks
    private ServiceRegistryServiceImpl serviceRegistryService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(ServiceInstance.class);
    }

    @Test
    @DisplayName("首次注册 - 新行状态置 UP，元数据按 JSON 落库")
    void should_insertUpInstance_when_firstRegister() {
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(null);
        ServiceRegisterDTO dto = registerDto();
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("version", "1.0.0");
        dto.setMetadata(metadata);

        serviceRegistryService.register(dto);

        ArgumentCaptor<ServiceInstance> captor = ArgumentCaptor.forClass(ServiceInstance.class);
        verify(serviceInstanceMapper).insert(captor.capture());
        verify(serviceInstanceMapper, never()).updateById(any(ServiceInstance.class));
        ServiceInstance saved = captor.getValue();
        assertEquals("UP", saved.getStatus());
        assertEquals("{\"version\":\"1.0.0\"}", saved.getMetadata());
        assertNotNull(saved.getLastHeartbeat());
    }

    @Test
    @DisplayName("重复注册 - 原地更新并沿用原行，不再插一条")
    void should_updateExisting_when_reRegister() {
        ServiceInstance existing = instance(11L, "analysis-service", "a-1", "UP");
        existing.setHost("127.0.0.1");
        existing.setPort(8085);
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(existing);

        ServiceRegisterDTO dto = registerDto();
        dto.setHost("10.0.0.8");
        dto.setPort(9999);

        ServiceInstanceVO vo = serviceRegistryService.register(dto);

        verify(serviceInstanceMapper).updateById(existing);
        verify(serviceInstanceMapper, never()).insert(any(ServiceInstance.class));
        assertEquals("10.0.0.8", existing.getHost());
        assertEquals(Integer.valueOf(9999), existing.getPort());
        assertEquals("UP", existing.getStatus());
        assertEquals(Long.valueOf(11L), vo.getId());
    }

    @Test
    @DisplayName("重复注册不带元数据 - 保留库里原有的，不用 null 覆盖")
    void should_keepMetadata_when_reRegisterWithoutMetadata() {
        ServiceInstance existing = instance(11L, "analysis-service", "a-1", "UP");
        existing.setMetadata("{\"version\":\"1.0.0\"}");
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(existing);

        serviceRegistryService.register(registerDto());

        assertEquals("{\"version\":\"1.0.0\"}", existing.getMetadata());
    }

    @Test
    @DisplayName("元数据反序列化成 Map：BeanUtils 静默跳过的字段要显式补")
    void should_exposeMetadataAsMap_when_toVO() {
        ServiceInstance row = instance(1L, "alert-service", "al-1", "UP");
        row.setMetadata("{\"owner\":\"ops\",\"region\":\"cn-hangzhou\"}");
        when(serviceInstanceMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row));

        List<ServiceInstanceVO> vos = serviceRegistryService.getAllInstances();

        assertEquals(1, vos.size());
        assertEquals("ops", vos.get(0).getMetadata().get("owner"));
        assertEquals("cn-hangzhou", vos.get(0).getMetadata().get("region"));
    }

    @Test
    @DisplayName("脏元数据按空 Map 返回，不让监控页 500")
    void should_returnEmptyMetadata_when_jsonBroken() {
        ServiceInstance row = instance(1L, "alert-service", "al-1", "UP");
        row.setMetadata("{not-json");
        when(serviceInstanceMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row));

        assertEquals(Collections.emptyMap(),
                serviceRegistryService.getAllInstances().get(0).getMetadata());
    }

    @Test
    @DisplayName("注销不存在的实例 - 明确报错，不静默成功")
    void should_throw_when_deregisterUnknown() {
        when(serviceInstanceMapper.delete(anyWrapper())).thenReturn(0);

        BizException e = assertThrows(BizException.class,
                () -> serviceRegistryService.deregister("alert-service", "missing"));
        assertTrue(e.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("注销命中 - 按条件删除")
    void should_delete_when_deregisterKnown() {
        when(serviceInstanceMapper.delete(anyWrapper())).thenReturn(1);

        serviceRegistryService.deregister("alert-service", "al-1");

        verify(serviceInstanceMapper).delete(anyWrapper());
    }

    @Test
    @DisplayName("未知实例的心跳 - 记日志后丢弃，不凭空造一行")
    void should_ignoreHeartbeat_when_instanceUnknown() {
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(null);
        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setServiceName("alert-service");
        dto.setInstanceId("ghost");

        serviceRegistryService.heartbeat(dto);

        verify(serviceInstanceMapper, never()).updateById(any(ServiceInstance.class));
    }

    @Test
    @DisplayName("心跳带状态 - 刷新心跳时间并采纳状态")
    void should_adoptStatus_when_heartbeatCarriesIt() {
        ServiceInstance row = instance(3L, "alert-service", "al-1", "STARTING");
        LocalDateTime stale = LocalDateTime.now().minusMinutes(5);
        row.setLastHeartbeat(stale);
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(row);
        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setServiceName("alert-service");
        dto.setInstanceId("al-1");
        dto.setStatus("UP");

        serviceRegistryService.heartbeat(dto);

        assertEquals("UP", row.getStatus());
        assertTrue(row.getLastHeartbeat().isAfter(stale));
        verify(serviceInstanceMapper).updateById(row);
    }

    @Test
    @DisplayName("心跳不带状态 - 只续心跳，不改状态")
    void should_keepStatus_when_heartbeatOmitsIt() {
        ServiceInstance row = instance(3L, "alert-service", "al-1", "UP");
        when(serviceInstanceMapper.selectOne(anyWrapper())).thenReturn(row);
        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setServiceName("alert-service");
        dto.setInstanceId("al-1");

        serviceRegistryService.heartbeat(dto);

        assertEquals("UP", row.getStatus());
    }

    @Test
    @DisplayName("死亡检测 - 超时未心跳的实例逐个置 DOWN 并落库")
    void should_markDown_when_instancesStale() {
        ServiceInstance dead1 = instance(1L, "alert-service", "al-1", "UP");
        ServiceInstance dead2 = instance(2L, "analysis-service", "an-1", "STARTING");
        when(serviceInstanceMapper.selectList(any(Wrapper.class)))
                .thenReturn(Arrays.asList(dead1, dead2));

        serviceRegistryService.detectDeadInstances(90);

        assertEquals("DOWN", dead1.getStatus());
        assertEquals("DOWN", dead2.getStatus());
        verify(serviceInstanceMapper, times(2)).updateById(any(ServiceInstance.class));
    }

    @Test
    @DisplayName("死亡检测无命中 - 一次写都不做")
    void should_notWrite_when_noStaleInstances() {
        when(serviceInstanceMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.<ServiceInstance>emptyList());

        serviceRegistryService.detectDeadInstances(90);

        verify(serviceInstanceMapper, never()).updateById(any(ServiceInstance.class));
    }

    @Test
    @DisplayName("按服务名查实例 - 结果逐行转 VO")
    void should_mapEveryRow_when_listByServiceName() {
        when(serviceInstanceMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                instance(1L, "alert-service", "al-1", "UP"),
                instance(2L, "alert-service", "al-2", "DOWN")));

        List<ServiceInstanceVO> vos = serviceRegistryService.getInstanceList("alert-service");

        assertEquals(2, vos.size());
        assertEquals("al-2", vos.get(1).getInstanceId());
    }

    private static ServiceRegisterDTO registerDto() {
        ServiceRegisterDTO dto = new ServiceRegisterDTO();
        dto.setServiceName("analysis-service");
        dto.setInstanceId("an-1");
        dto.setHost("127.0.0.1");
        dto.setPort(8085);
        return dto;
    }

    private static ServiceInstance instance(Long id, String serviceName, String instanceId,
                                            String status) {
        ServiceInstance instance = new ServiceInstance();
        instance.setId(id);
        instance.setServiceName(serviceName);
        instance.setInstanceId(instanceId);
        instance.setStatus(status);
        instance.setLastHeartbeat(LocalDateTime.now());
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<ServiceInstance> anyWrapper() {
        return org.mockito.ArgumentMatchers.<Wrapper<ServiceInstance>>any();
    }
}
