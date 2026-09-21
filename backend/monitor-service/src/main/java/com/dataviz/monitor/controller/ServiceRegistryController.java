package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.monitor.dto.HeartbeatDTO;
import com.dataviz.monitor.dto.MetricReportDTO;
import com.dataviz.monitor.dto.ServiceRegisterDTO;
import com.dataviz.monitor.service.MetricService;
import com.dataviz.monitor.service.ServiceRegistryService;
import com.dataviz.monitor.vo.ServiceInstanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务注册控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/registry")
@RequiredArgsConstructor
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;
    private final MetricService metricService;

    /**
     * 注册服务实例
     */
    @PostMapping("/register")
    public R<ServiceInstanceVO> register(@RequestBody ServiceRegisterDTO registerDTO) {
        return R.ok(serviceRegistryService.register(registerDTO));
    }

    /**
     * 注销服务实例
     */
    @DeleteMapping("/deregister")
    public R<Void> deregister(
            @RequestParam String serviceName,
            @RequestParam String instanceId) {
        serviceRegistryService.deregister(serviceName, instanceId);
        return R.ok();
    }

    /**
     * 心跳上报
     */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat(@RequestBody HeartbeatDTO heartbeatDTO) {
        serviceRegistryService.heartbeat(heartbeatDTO);
        return R.ok();
    }

    /**
     * 指标上报
     */
    @PostMapping("/metrics/report")
    public R<Void> reportMetric(@RequestBody MetricReportDTO reportDTO) {
        metricService.reportMetrics(reportDTO);
        return R.ok();
    }

    /**
     * 批量指标上报
     */
    @PostMapping("/metrics/reportBatch")
    public R<Void> reportMetricsBatch(@RequestBody List<MetricReportDTO> reportDTOs) {
        metricService.reportMetricsBatch(reportDTOs);
        return R.ok();
    }
}
