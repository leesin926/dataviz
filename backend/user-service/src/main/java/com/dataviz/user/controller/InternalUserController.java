package com.dataviz.user.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.user.service.UserService;
import com.dataviz.user.vo.AuthUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内部认证接口 —— 只给 auth-service 直连调用（RBAC 数据唯一归属 db_user）。
 * <p>
 * 访问控制是三层：网关对 {@code /api/user/internal/**} 直接拒绝（外部穿不进来）→ 服务层 AuthInterceptor
 * 免拦（认证时还没有 JWT 可用）→ 本路径由 {@code InternalApiInterceptor} 校验 X-Internal-Token。
 * 响应体含密码哈希，任何情况下都不要把这些端点复制成对外接口。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/internal")
@RequiredArgsConstructor
@Tag(name = "Internal Auth API", description = "服务间内部接口，不对公网开放")
public class InternalUserController {

    private final UserService userService;

    /**
     * 按用户名查认证用户。查不到返回 data=null —— 认证侧据此判定"用户不存在"，
     * 与"内部接口调用失败"区分开，避免把库故障报成口令错误。
     */
    @GetMapping("/auth-user")
    @Operation(summary = "Find auth user by username (internal)")
    public R<AuthUserVO> getByUsername(@RequestParam("username") String username) {
        return R.ok(userService.findAuthUserByUsername(username));
    }

    @GetMapping("/auth-user/{userId}")
    @Operation(summary = "Find auth user by id (internal)")
    public R<AuthUserVO> getById(@PathVariable("userId") Long userId) {
        return R.ok(userService.findAuthUserById(userId));
    }

    @GetMapping("/auth-user/{userId}/role-codes")
    @Operation(summary = "List role codes of a user (internal)")
    public R<List<String>> getRoleCodes(@PathVariable("userId") Long userId) {
        return R.ok(userService.getRoleCodes(userId));
    }

    @GetMapping("/auth-user/{userId}/permission-codes")
    @Operation(summary = "List permission codes of a user (internal)")
    public R<List<String>> getPermissionCodes(@PathVariable("userId") Long userId) {
        return R.ok(userService.getUserPermissions(userId));
    }

    /** 登录成功后回写登录时间；时间由本服务自己盖，省掉跨服务的时钟与时区口径。 */
    @PutMapping("/auth-user/{userId}/login-info")
    @Operation(summary = "Record login time (internal)")
    public R<Void> recordLoginInfo(@PathVariable("userId") Long userId) {
        userService.recordLoginInfo(userId);
        return R.ok();
    }
}
