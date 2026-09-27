package com.dataviz.user.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.dto.UserCreateDTO;
import com.dataviz.user.dto.UserQueryDTO;
import com.dataviz.user.dto.UserUpdateDTO;
import com.dataviz.user.vo.AuthUserVO;
import com.dataviz.user.vo.UserVO;
import java.util.List;

public interface UserService {
    Long createUser(UserCreateDTO dto, Long tenantId);
    // 这六条一律带 tenantId：路径上的 id 是谁都能猜的输入，只有请求头里的租户是网关从 JWT 换出来的。
    // getUserPermissions 刻意不加 —— 它同时服务 /user/internal/**（登录期全局取码，本来就不该带租户）。
    void updateUser(Long id, UserUpdateDTO dto, Long tenantId);
    void deleteUser(Long id, Long tenantId);
    UserVO getUserById(Long id, Long tenantId);
    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);
    void toggleStatus(Long id, Integer status, Long tenantId);
    void resetPassword(Long id, String newPassword, Long tenantId);
    void assignRoles(Long userId, List<Long> roleIds, Long tenantId);
    List<String> getUserPermissions(Long userId);

    /** 以下方法只服务于 /user/internal/**，供 auth-service 认证时读取；查不到返回 null，不抛异常 */
    AuthUserVO findAuthUserByUsername(String username);
    AuthUserVO findAuthUserByPhone(String phone);
    AuthUserVO findAuthUserById(Long id);
    List<String> getRoleCodes(Long userId);
    void recordLoginInfo(Long userId);
}
