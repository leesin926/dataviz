package com.dataviz.user.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.user.dto.UserCreateDTO;
import com.dataviz.user.dto.UserQueryDTO;
import com.dataviz.user.dto.UserUpdateDTO;
import com.dataviz.user.service.UserService;
import com.dataviz.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User management controller.
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User CRUD, status toggle, password reset, role assignment")
public class UserController {

    private final UserService userService;

    @PostMapping
    @RequiresPermission("system:user:add")
    @Operation(summary = "Create user")
    public R<Long> createUser(@RequestBody @Valid UserCreateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        Long userId = userService.createUser(dto, Long.valueOf(tenantId));
        return R.ok(userId);
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:user:edit")
    @Operation(summary = "Update user")
    public R<Void> updateUser(@PathVariable("id") Long id, @RequestBody @Valid UserUpdateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        userService.updateUser(id, dto, Long.valueOf(tenantId));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:user:delete")
    @Operation(summary = "Delete user")
    public R<Void> deleteUser(@PathVariable("id") Long id,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        userService.deleteUser(id, Long.valueOf(tenantId));
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:user:query")
    @Operation(summary = "Get user by ID")
    public R<UserVO> getUserById(@PathVariable("id") Long id,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(userService.getUserById(id, Long.valueOf(tenantId)));
    }

    @GetMapping("/page")
    @RequiresPermission("system:user:list")
    @Operation(summary = "List users with pagination")
    public R<PageResult<UserVO>> listUsers(UserQueryDTO queryDTO,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        queryDTO.setTenantId(Long.valueOf(tenantId));
        return R.ok(userService.listUsers(queryDTO));
    }

    @PutMapping("/{id}/status/{status}")
    @RequiresPermission("system:user:edit")
    @Operation(summary = "Toggle user status")
    public R<Void> toggleStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        userService.toggleStatus(id, status, Long.valueOf(tenantId));
        return R.ok();
    }

    @PutMapping("/{id}/password/reset")
    @RequiresPermission("system:user:edit")
    @Operation(summary = "Reset user password")
    public R<Void> resetPassword(@PathVariable("id") Long id,
                                       @RequestParam("newPassword") String newPassword,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        userService.resetPassword(id, newPassword, Long.valueOf(tenantId));
        return R.ok();
    }

    @PostMapping("/{id}/roles")
    @RequiresPermission("system:user:edit")
    @Operation(summary = "Assign roles to user")
    public R<Void> assignRoles(@PathVariable("id") Long id, @RequestBody List<Long> roleIds,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        userService.assignRoles(id, roleIds, Long.valueOf(tenantId));
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    @RequiresPermission("system:user:query")
    @Operation(summary = "Get user permissions")
    public R<List<String>> getUserPermissions(@PathVariable("id") Long id,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        // getUserPermissions 本身不带租户条件（登录期取码要用同一条），管理面先过一次归属校验再取码
        userService.getUserById(id, Long.valueOf(tenantId));
        return R.ok(userService.getUserPermissions(id));
    }

    /**
     * 只回"自己"：网关用 JWT 换出的 X-User-Id，任何登录用户都该能读，故刻意不加 @RequiresPermission
     * （加了就得给每个角色发一个"看自己资料"的权限码，那不是权限而是必需品）。
     */
    @GetMapping("/current")
    @Operation(summary = "Get current logged-in user info")
    public R<UserVO> getCurrentUser(@RequestHeader("X-User-Id") String userId,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(userService.getUserById(Long.valueOf(userId), Long.valueOf(tenantId)));
    }
}
