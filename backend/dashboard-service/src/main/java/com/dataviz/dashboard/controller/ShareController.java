package com.dataviz.dashboard.controller;

import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/share")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 dashboard:read（PermissionInterceptor 会回落到类上的注解），改状态的动作在方法上抬到 dashboard:write。
@RequiresPermission("dashboard:read")
public class ShareController {

    @GetMapping("/link/{dashboardId}")
    @RequiresPermission("dashboard:write")
    public Map<String, Object> getShareLink(@PathVariable Long dashboardId) {
        String shareToken = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> result = new HashMap<>();
        result.put("dashboardId", dashboardId);
        result.put("shareToken", shareToken);
        result.put("shareUrl", "/share/" + shareToken);
        log.info("Generated share link for dashboard: {}", dashboardId);
        return result;
    }

    @GetMapping("/embed/{dashboardId}")
    @RequiresPermission("dashboard:write")
    public Map<String, Object> getEmbedCode(@PathVariable Long dashboardId) {
        String shareToken = UUID.randomUUID().toString().replace("-", "");
        String embedUrl = "/embed/" + shareToken;
        String embedHtml = "<iframe src=\"" + embedUrl + "\" width=\"100%\" height=\"600\" frameborder=\"0\"></iframe>";
        Map<String, Object> result = new HashMap<>();
        result.put("dashboardId", dashboardId);
        result.put("embedUrl", embedUrl);
        result.put("embedHtml", embedHtml);
        log.info("Generated embed code for dashboard: {}", dashboardId);
        return result;
    }
}
