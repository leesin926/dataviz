package com.dataviz.dashboard.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.dashboard.dto.DashboardCreateDTO;
import com.dataviz.dashboard.dto.DashboardUpdateDTO;
import com.dataviz.dashboard.service.DashboardService;
import com.dataviz.dashboard.vo.DashboardListVO;
import com.dataviz.dashboard.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 dashboard:read（PermissionInterceptor 会回落到类上的注解），改状态的动作在方法上抬到 dashboard:write。
@RequiresPermission("dashboard:read")
public class DashboardController {

    private final DashboardService dashboardService;

    @PostMapping
    @RequiresPermission("dashboard:write")
    public Long create(@RequestBody DashboardCreateDTO dto) {
        return dashboardService.create(dto);
    }

    @PutMapping
    @RequiresPermission("dashboard:write")
    public void update(@RequestBody DashboardUpdateDTO dto) {
        dashboardService.update(dto);
    }

    @GetMapping("/{id}")
    public DashboardVO getById(@PathVariable Long id) {
        return dashboardService.getById(id);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("dashboard:write")
    public void delete(@PathVariable Long id) {
        dashboardService.delete(id);
    }

    @GetMapping("/page")
    public Page<DashboardListVO> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return dashboardService.page(pageNum, pageSize, keyword, status);
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("dashboard:write")
    public void publish(@PathVariable Long id) {
        dashboardService.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    @RequiresPermission("dashboard:write")
    public void unpublish(@PathVariable Long id) {
        dashboardService.unpublish(id);
    }

    @PostMapping("/{id}/copy")
    @RequiresPermission("dashboard:write")
    public Long copy(@PathVariable Long id) {
        return dashboardService.copy(id);
    }

    @GetMapping("/templates")
    public List<DashboardListVO> listTemplates() {
        return dashboardService.listTemplates();
    }
}
