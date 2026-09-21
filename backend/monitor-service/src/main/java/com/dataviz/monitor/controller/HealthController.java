package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.monitor.service.HealthCheckService;
import com.dataviz.monitor.vo.ServiceHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;

    /**
     * 检查服务健康状态
     */
    @GetMapping("/{serviceName}")
    public R<ServiceHealthVO> check(@PathVariable String serviceName) {
        return R.ok(healthCheckService.checkHealth(serviceName));
    }

    /**
     * 更新服务实例健康状态
     */
    @PutMapping("/update")
    public R<Void> updateHealth(
            @RequestParam String serviceName,
            @RequestParam String instanceId,
            @RequestParam String status) {
        healthCheckService.updateHealth(serviceName, instanceId, status);
        return R.ok();
    }
}
