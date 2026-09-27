package com.dataviz.admin.controller;

import com.dataviz.admin.dto.TenantCreateDTO;
import com.dataviz.admin.dto.TenantUpdateDTO;
import com.dataviz.admin.service.TenantService;
import com.dataviz.admin.vo.TenantVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenant")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 platform:read（PermissionInterceptor 会回落到类上的注解），建/改/删租户与启停租户的动作抬到 platform:write。
@RequiresPermission("platform:read")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @RequiresPermission("platform:write")
    public R<Long> create(@RequestBody TenantCreateDTO dto) {
        return R.ok(tenantService.create(dto));
    }

    @PutMapping
    @RequiresPermission("platform:write")
    public R<Void> update(@RequestBody TenantUpdateDTO dto) {
        tenantService.update(dto);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<TenantVO> getById(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("platform:write")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    public R<PageResult<TenantVO>> page(PageQuery pageQuery,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        return R.ok(tenantService.page(pageQuery, keyword, status));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("platform:write")
    public R<Void> enable(@PathVariable Long id) {
        tenantService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("platform:write")
    public R<Void> disable(@PathVariable Long id) {
        tenantService.disable(id);
        return R.ok();
    }
}
