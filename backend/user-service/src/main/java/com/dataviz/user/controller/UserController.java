package com.dataviz.user.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
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
    @Operation(summary = "Create user")
    public R<Long> createUser(@RequestBody @Valid UserCreateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        Long userId = userService.createUser(dto, Long.valueOf(tenantId));
        return R.ok(userId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public R<Void> updateUser(@PathVariable("id") Long id, @RequestBody @Valid UserUpdateDTO dto) {
        userService.updateUser(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    public R<Void> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public R<UserVO> getUserById(@PathVariable("id") Long id) {
        return R.ok(userService.getUserById(id));
    }

    @GetMapping("/page")
    @Operation(summary = "List users with pagination")
    public R<PageResult<UserVO>> listUsers(UserQueryDTO queryDTO,
                                                 @RequestHeader("X-Tenant-Id") String tenantId) {
        queryDTO.setTenantId(Long.valueOf(tenantId));
        return R.ok(userService.listUsers(queryDTO));
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "Toggle user status")
    public R<Void> toggleStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        userService.toggleStatus(id, status);
        return R.ok();
    }

    @PutMapping("/{id}/password/reset")
    @Operation(summary = "Reset user password")
    public R<Void> resetPassword(@PathVariable("id") Long id,
                                       @RequestParam("newPassword") String newPassword) {
        userService.resetPassword(id, newPassword);
        return R.ok();
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "Assign roles to user")
    public R<Void> assignRoles(@PathVariable("id") Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "Get user permissions")
    public R<List<String>> getUserPermissions(@PathVariable("id") Long id) {
        return R.ok(userService.getUserPermissions(id));
    }

    @GetMapping("/current")
    @Operation(summary = "Get current logged-in user info")
    public R<UserVO> getCurrentUser(@RequestHeader("X-User-Id") String userId) {
        return R.ok(userService.getUserById(Long.valueOf(userId)));
    }
}
