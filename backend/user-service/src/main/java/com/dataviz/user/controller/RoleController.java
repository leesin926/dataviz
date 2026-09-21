package com.dataviz.user.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.user.service.RoleService;
import com.dataviz.user.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role management controller.
 */
@Slf4j
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Role CRUD and permission assignment")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @Operation(summary = "Create role")
    public R<Long> createRole(@RequestBody RoleVO roleVO,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.createRole(roleVO, Long.valueOf(tenantId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role")
    public R<Void> updateRole(@PathVariable("id") Long id, @RequestBody RoleVO roleVO) {
        roleService.updateRole(id, roleVO);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role")
    public R<Void> deleteRole(@PathVariable("id") Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID")
    public R<RoleVO> getRoleById(@PathVariable("id") Long id) {
        return R.ok(roleService.getRoleById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "List all roles")
    public R<List<RoleVO>> listRoles(@RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.listRoles(Long.valueOf(tenantId)));
    }

    @GetMapping("/page")
    @Operation(summary = "List roles with pagination")
    public R<PageResult<RoleVO>> pageRoles(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.pageRoles(page, size, Long.valueOf(tenantId)));
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "Assign permissions to role")
    public R<Void> assignPermissions(@PathVariable("id") Long id,
                                           @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return R.ok();
    }
}
