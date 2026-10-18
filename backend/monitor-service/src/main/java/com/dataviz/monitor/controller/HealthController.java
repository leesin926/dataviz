package com.dataviz.monitor.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
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
@RequiresPermission("monitor:read")
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
     * <p>写档码是<b>临时口径</b>，不是这件事的最终归属：这条端点的预期调用方是服务自己（机器），
     * 机器手上没有用户会话，正常形态应该是 {@code /internal/} 路径 + X-Internal-Token（同 datasource、
     * user 两个服务）。本轮先按 {@code monitor:write} 封住，是因为在改完归属之前它对着的是
     * "任何登录用户都能翻任意实例的健康状态"。改成内部端点要动路径，属需拍板的变更，见 R1 台账。</p>
     */
    @PutMapping("/update")
    @RequiresPermission("monitor:write")
    public R<Void> updateHealth(
            @RequestParam String serviceName,
            @RequestParam String instanceId,
            @RequestParam String status) {
        healthCheckService.updateHealth(serviceName, instanceId, status);
        return R.ok();
    }
}
