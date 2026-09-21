package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.monitor.service.MetricService;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.vo.MetricHistoryVO;
import com.dataviz.monitor.vo.MetricVO;
import com.dataviz.monitor.vo.ServiceInstanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
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
     * 获取指标历史数据
     */
    @GetMapping("/metrics/history")
    public R<List<MetricHistoryVO>> getMetricHistory(
            @RequestParam String serviceName,
            @RequestParam String metricName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return R.ok(metricService.getMetricHistory(serviceName, metricName, startTime, endTime));
    }
}
