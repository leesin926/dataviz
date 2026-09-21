package com.dataviz.admin.controller;

import com.dataviz.admin.dto.TenantCreateDTO;
import com.dataviz.admin.dto.TenantUpdateDTO;
import com.dataviz.admin.service.TenantService;
import com.dataviz.admin.vo.TenantVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public R<Long> create(@RequestBody TenantCreateDTO dto) {
        return R.ok(tenantService.create(dto));
    }

    @PutMapping
    public R<Void> update(@RequestBody TenantUpdateDTO dto) {
        tenantService.update(dto);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<TenantVO> getById(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @DeleteMapping("/{id}")
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
    public R<Void> enable(@PathVariable Long id) {
        tenantService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        tenantService.disable(id);
        return R.ok();
    }
}
