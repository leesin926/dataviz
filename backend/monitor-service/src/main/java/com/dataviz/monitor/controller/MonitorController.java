package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.monitor.service.MetricService;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.vo.MetricHistoryVO;
import com.dataviz.monitor.vo.MetricVO;
import com.dataviz.monitor.vo.ServiceInstanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控查询控制器。四条端点全是只读，类级读码覆盖，没有方法级写码。
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@RequiresPermission("monitor:read")
public class MonitorController {

    private final ServiceRegistryService serviceRegistryService;
    private final MetricService metricService;

    /**
     * 获取服务列表（所有注册的服务实例）
     */
    @GetMapping("/services")
    public R<List<ServiceInstanceVO>> getServiceList() {
        return R.ok(serviceRegistryService.getAllInstances());
    }

    /**
     * 获取指定服务的实例列表
     */
    @GetMapping("/services/{serviceName}/instances")
    public R<List<ServiceInstanceVO>> getInstanceList(@PathVariable String serviceName) {
        return R.ok(serviceRegistryService.getInstanceList(serviceName));
    }

    /**
     * 获取服务当前指标
     */
    @GetMapping("/metrics")
    public R<List<MetricVO>> getMetrics(
            @RequestParam String serviceName,
            @RequestParam(required = false) String instanceId) {
        return R.ok(metricService.getMetrics(serviceName, instanceId));
    }

    /**
     * 获取指标历史数据。
     *
     * <p>时间入参是 ISO 本地日期时间（{@code 2026-11-07T09:30:00}），与管理端
     * {@code ServiceMonitor.vue} 那个区间选择器的 {@code value-format} 同一形状。
     * 这两个 {@code @DateTimeFormat} 不是修饰：Spring Boot 默认不给裸 {@code LocalDateTime}
     * 查询参数装 ISO 解析器（走 locale 的 SHORT 样式），带 {@code T} 的值会在进方法之前就被拒成 400，
     * 于是"选了时间段就没数据"看起来像指标缺失，实际是入参没解析上（同 API-170，
     * openapi 侧那条已用运行时判据证实）。
     */
    @GetMapping("/metrics/history")
    public R<List<MetricHistoryVO>> getMetricHistory(
            @RequestParam String serviceName,
            @RequestParam String metricName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(metricService.getMetricHistory(serviceName, metricName, startTime, endTime));
    }
}
