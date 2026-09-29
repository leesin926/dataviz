package com.dataviz.user.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
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
    @RequiresPermission("system:role:add")
    @Operation(summary = "Create role")
    public R<Long> createRole(@RequestBody RoleVO roleVO,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.createRole(roleVO, Long.valueOf(tenantId)));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:role:edit")
    @Operation(summary = "Update role")
    public R<Void> updateRole(@PathVariable("id") Long id, @RequestBody RoleVO roleVO,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        roleService.updateRole(id, roleVO, Long.valueOf(tenantId));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:role:delete")
    @Operation(summary = "Delete role")
    public R<Void> deleteRole(@PathVariable("id") Long id,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        roleService.deleteRole(id, Long.valueOf(tenantId));
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:role:list")
    @Operation(summary = "Get role by ID")
    public R<RoleVO> getRoleById(@PathVariable("id") Long id,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.getRoleById(id, Long.valueOf(tenantId)));
    }

    @GetMapping("/list")
    @RequiresPermission("system:role:list")
    @Operation(summary = "List all roles")
    public R<List<RoleVO>> listRoles(@RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.listRoles(Long.valueOf(tenantId)));
    }

    @GetMapping("/page")
    @RequiresPermission("system:role:list")
    @Operation(summary = "List roles with pagination")
    public R<PageResult<RoleVO>> pageRoles(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.pageRoles(page, size, Long.valueOf(tenantId)));
    }

    /**
     * 角色当前的授权 id —— 给"分配权限"界面回显用。
     * 用 list 码而不是 edit：角色管理页本身的数据源就是 /role/list（system:role:list），
     * 所以"进得来页面却打不开抽屉"这种组合不会出现。
     */
    @GetMapping("/{id}/permissions")
    @RequiresPermission("system:role:list")
    @Operation(summary = "List permission ids assigned to a role")
    public R<List<Long>> getRolePermissions(@PathVariable("id") Long id,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(roleService.getRolePermissionIds(id, Long.valueOf(tenantId)));
    }

    @PostMapping("/{id}/permissions")
    @RequiresPermission("system:role:edit")
    @Operation(summary = "Assign permissions to role")
    public R<Void> assignPermissions(@PathVariable("id") Long id,
                                     @RequestBody List<Long> permissionIds,
                                     @RequestHeader("X-Tenant-Id") String tenantId) {
        roleService.assignPermissions(id, permissionIds, Long.valueOf(tenantId));
        return R.ok();
    }
}
