package com.dataviz.user.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.service.RoleService;
import com.dataviz.user.vo.PermissionTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Permission management controller.
 */
@Slf4j
@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "Permission tree and CRUD operations")
// 类级声明对"本控制器全部端点"生效（PermissionInterceptor 会回落到 getBeanType() 上的注解），
// 下面三个写接口再用方法级覆盖 —— 读菜单树和改菜单树的授权面本来就不该是同一个码。
@RequiresPermission("system:menu:list")
public class PermissionController {

    private final RoleService roleService;

    @GetMapping("/tree")
    @Operation(summary = "Get permission tree")
    public R<List<PermissionTreeVO>> getPermissionTree() {
        return R.ok(roleService.getPermissionTree());
    }

    @PostMapping
    @RequiresPermission("system:menu:add")
    @Operation(summary = "Create permission")
    public R<Long> createPermission(@RequestBody SysPermission permission) {
        return R.ok(roleService.createPermission(permission));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:menu:edit")
    @Operation(summary = "Update permission")
    public R<Void> updatePermission(@PathVariable("id") Long id, @RequestBody SysPermission permission) {
        roleService.updatePermission(id, permission);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:menu:delete")
    @Operation(summary = "Delete permission")
    public R<Void> deletePermission(@PathVariable("id") Long id) {
        roleService.deletePermission(id);
        return R.ok();
    }
}
