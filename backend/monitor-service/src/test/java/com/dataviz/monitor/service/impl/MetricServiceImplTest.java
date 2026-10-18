package com.dataviz.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.monitor.dto.MetricReportDTO;
import com.dataviz.monitor.entity.MonitorMetric;
import com.dataviz.monitor.mapper.MonitorMetricMapper;
import com.dataviz.monitor.test.MybatisPlusTableInfoHelper;
import com.dataviz.monitor.vo.MetricHistoryVO;
import com.dataviz.monitor.vo.MetricVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指标读写。这里最要紧的一条是 {@code getMetrics} 的语义：它是"当前指标"，
 * 不是历史表倒序全量 —— 少了按 (实例, 指标名) 去重，监控页拿到的就是几个月的点。
 */
@ExtendWith(MockitoExtension.class)
class MetricServiceImplTest {

    @Mock
    private MonitorMetricMapper monitorMetricMapper;

    @InjectMocks
    private MetricServiceImpl metricService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(MonitorMetric.class);
    }

    @Test
    @DisplayName("上报单条 - 字段逐项落库，标签按 JSON 存")
    void should_persistFields_when_report() {
        MetricReportDTO dto = new MetricReportDTO();
        dto.setServiceName("analysis-service");
        dto.setInstanceId("an-1");
        dto.setMetricName("jvm_heap_used_mb");
        dto.setMetricValue(new BigDecimal("512.25"));
        dto.setMetricType("GAUGE");
        java.util.Map<String, String> tags = new java.util.LinkedHashMap<String, String>();
        tags.put("env", "prod");
        dto.setTags(tags);
        LocalDateTime ts = LocalDateTime.now().minusSeconds(30);
        dto.setTimestamp(ts);

        metricService.reportMetrics(dto);

        ArgumentCaptor<MonitorMetric> captor = ArgumentCaptor.forClass(MonitorMetric.class);
        verify(monitorMetricMapper).insert(captor.capture());
        MonitorMetric saved = captor.getValue();
        assertEquals("analysis-service", saved.getServiceName());
        assertEquals("jvm_heap_used_mb", saved.getMetricName());
        assertEquals(new BigDecimal("512.25"), saved.getMetricValue());
        assertEquals("GAUGE", saved.getMetricType());
        assertEquals("{\"env\":\"prod\"}", saved.getTags());
        assertEquals(ts, saved.getTimestamp());
    }

    @Test
    @DisplayName("上报未带时间戳 - 由服务端补 now，不写 null")
    void should_stampNow_when_timestampMissing() {
        MetricReportDTO dto = new MetricReportDTO();
        dto.setServiceName("analysis-service");
        dto.setInstanceId("an-1");
        dto.setMetricName("cpu_usage");
        dto.setMetricValue(BigDecimal.ONE);

        metricService.reportMetrics(dto);

        ArgumentCaptor<MonitorMetric> captor = ArgumentCaptor.forClass(MonitorMetric.class);
        verify(monitorMetricMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getTimestamp());
        assertNull(captor.getValue().getTags());
    }

    @Test
    @DisplayName("批量上报 - 逐条写入，条数与入参一致")
    void should_insertEveryItem_when_reportBatch() {
        MetricReportDTO a = new MetricReportDTO();
        a.setServiceName("s");
        a.setMetricName("cpu");
        MetricReportDTO b = new MetricReportDTO();
        b.setServiceName("s");
        b.setMetricName("mem");

        metricService.reportMetricsBatch(Arrays.asList(a, b));

        verify(monitorMetricMapper, times(2)).insert(any(MonitorMetric.class));
    }

    @Test
    @DisplayName("当前指标 - 同实例同名只留最新一条，不同实例互不覆盖")
    void should_keepLatestPerInstanceMetric_when_currentMetrics() {
        LocalDateTime now = LocalDateTime.now();
        // 查询侧已按 timestamp 倒序，这里模拟真实返回顺序
        when(monitorMetricMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                metric(1L, "an-1", "cpu", "90", now),
                metric(2L, "an-1", "cpu", "80", now.minusMinutes(1)),
                metric(3L, "an-1", "mem", "512", now.minusMinutes(2)),
                metric(4L, "an-2", "cpu", "30", now.minusMinutes(3))));

        List<MetricVO> vos = metricService.getMetrics("analysis-service", null);

        assertEquals(3, vos.size());
        assertEquals("90", vos.get(0).getMetricValue().toPlainString());
        assertEquals("an-1", vos.get(0).getInstanceId());
        assertEquals("mem", vos.get(1).getMetricName());
        assertEquals("an-2", vos.get(2).getInstanceId());
    }

    @Test
    @DisplayName("标签从 JSON 还原成 Map 交给前端")
    void should_parseTags_when_currentMetrics() {
        MonitorMetric row = metric(1L, "an-1", "cpu", "90", LocalDateTime.now());
        row.setTags("{\"env\":\"prod\",\"unit\":\"percent\"}");
        when(monitorMetricMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row));

        MetricVO vo = metricService.getMetrics("analysis-service", "an-1").get(0);

        assertEquals("prod", vo.getTags().get("env"));
        assertEquals("percent", vo.getTags().get("unit"));
    }

    @Test
    @DisplayName("历史曲线 - 逐点映射，不做去重")
    void should_mapEveryPoint_when_history() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        when(monitorMetricMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                metric(1L, "an-1", "cpu", "80", t1),
                metric(2L, "an-1", "cpu", "90", t2)));

        List<MetricHistoryVO> history = metricService.getMetricHistory(
                "analysis-service", "cpu", t1.minusMinutes(1), t2);

        assertEquals(2, history.size());
        assertEquals("80", history.get(0).getMetricValue().toPlainString());
        assertEquals("90", history.get(1).getMetricValue().toPlainString());
        assertEquals(t1, history.get(0).getTimestamp());
        assertEquals("cpu", history.get(0).getMetricName());
    }

    private static MonitorMetric metric(Long id, String instanceId, String metricName,
                                        String value, LocalDateTime timestamp) {
        return MonitorMetric.builder()
                .id(id).serviceName("analysis-service").instanceId(instanceId)
                .metricName(metricName).metricValue(new BigDecimal(value))
                .metricType("GAUGE").timestamp(timestamp).build();
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<MonitorMetric> anyWrapper() {
        return org.mockito.ArgumentMatchers.<Wrapper<MonitorMetric>>any();
    }
}
