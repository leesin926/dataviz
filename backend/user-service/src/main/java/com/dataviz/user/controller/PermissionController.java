package com.dataviz.user.controller;

import com.dataviz.common.core.result.R;
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
public class PermissionController {

    private final RoleService roleService;

    @GetMapping("/tree")
    @Operation(summary = "Get permission tree")
    public R<List<PermissionTreeVO>> getPermissionTree() {
        return R.ok(roleService.getPermissionTree());
    }

    @PostMapping
    @Operation(summary = "Create permission")
    public R<Long> createPermission(@RequestBody SysPermission permission) {
        return R.ok(roleService.createPermission(permission));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update permission")
    public R<Void> updatePermission(@PathVariable("id") Long id, @RequestBody SysPermission permission) {
        roleService.updatePermission(id, permission);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission")
    public R<Void> deletePermission(@PathVariable("id") Long id) {
        roleService.deletePermission(id);
        return R.ok();
    }
}
