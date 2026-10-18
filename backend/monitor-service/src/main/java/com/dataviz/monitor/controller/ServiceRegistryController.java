package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
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
 * <p>五条端点全按 {@code monitor:write} 封住，这是<b>临时口径</b>：注册/心跳/上报的预期调用方是
 * 服务进程本身，它手上没有用户会话，本不该用用户权限码来判。全类现在<b>零调用方</b>
 * （后端与前端都搜不到对 {@code /api/monitor/registry} 的引用），所以先封住不会打断任何链路，
 * 也能挡住"任何登录用户都能伪造注册、注销、灌指标"。
 * 真要启用自注册，正确做法是把这组路径挪进 {@code /internal/} 约定 + X-Internal-Token，
 * 那要改路径，属需拍板的变更 —— 见 R1 台账 B 档。</p>
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
    @RequiresPermission("monitor:write")
    public R<ServiceInstanceVO> register(@RequestBody ServiceRegisterDTO registerDTO) {
        return R.ok(serviceRegistryService.register(registerDTO));
    }

    /**
     * 注销服务实例
     */
    @DeleteMapping("/deregister")
    @RequiresPermission("monitor:write")
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
    @RequiresPermission("monitor:write")
    public R<Void> heartbeat(@RequestBody HeartbeatDTO heartbeatDTO) {
        serviceRegistryService.heartbeat(heartbeatDTO);
        return R.ok();
    }

    /**
     * 指标上报
     */
    @PostMapping("/metrics/report")
    @RequiresPermission("monitor:write")
    public R<Void> reportMetric(@RequestBody MetricReportDTO reportDTO) {
        metricService.reportMetrics(reportDTO);
        return R.ok();
    }

    /**
     * 批量指标上报
     */
    @PostMapping("/metrics/reportBatch")
    @RequiresPermission("monitor:write")
    public R<Void> reportMetricsBatch(@RequestBody List<MetricReportDTO> reportDTOs) {
        metricService.reportMetricsBatch(reportDTOs);
        return R.ok();
    }
}
