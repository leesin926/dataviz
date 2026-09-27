package com.dataviz.user.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.service.DeptService;
import com.dataviz.user.vo.DeptTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Department management controller.
 * <p>
 * 四条码（{@code system:dept:list/add/edit/delete}）与 {@code DeptManage.vue} 的
 * {@code meta.permission} 同源（路线乙）。<strong>读树单独成码而不是复用 {@code system:user:list}</strong>：
 * 复用等于"能管用户就能删部门"，两件事的授权对象本来可以不同。
 * 代价是<em>有联动</em>——管理端用户页左侧的部门树打的也是 {@code /dept/tree}，
 * 所以<strong>被授予 {@code system:user:*} 的角色必须同时拿到 {@code system:dept:list}</strong>，
 * 否则那个选择器会当场 403（播种脚本里已按这条给 role 2 补上）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "Department tree and CRUD operations")
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @RequiresPermission("system:dept:list")
    @Operation(summary = "Get department tree")
    public R<List<DeptTreeVO>> getDeptTree(@RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(deptService.getDeptTree(Long.valueOf(tenantId)));
    }

    @PostMapping
    @RequiresPermission("system:dept:add")
    @Operation(summary = "Create department")
    public R<Long> createDept(@RequestBody @Valid DeptCreateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(deptService.createDept(dto, Long.valueOf(tenantId)));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:dept:edit")
    @Operation(summary = "Update department")
    public R<Void> updateDept(@PathVariable("id") Long id, @RequestBody @Valid DeptCreateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        deptService.updateDept(id, dto, Long.valueOf(tenantId));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:delete")
    @Operation(summary = "Delete department")
    public R<Void> deleteDept(@PathVariable("id") Long id, @RequestHeader("X-Tenant-Id") String tenantId) {
        deptService.deleteDept(id, Long.valueOf(tenantId));
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:dept:list")
    @Operation(summary = "Get department by ID")
    public R<DeptTreeVO> getDeptById(@PathVariable("id") Long id, @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(deptService.getDeptById(id, Long.valueOf(tenantId)));
    }
}
