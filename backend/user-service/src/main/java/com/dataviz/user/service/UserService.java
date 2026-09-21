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
    void updateUser(Long id, UserUpdateDTO dto);
    void deleteUser(Long id);
    UserVO getUserById(Long id);
    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);
    void toggleStatus(Long id, Integer status);
    void resetPassword(Long id, String newPassword);
    void assignRoles(Long userId, List<Long> roleIds);
    List<String> getUserPermissions(Long userId);

    /** 以下四个方法只服务于 /user/internal/**，供 auth-service 认证时读取；查不到返回 null，不抛异常 */
    AuthUserVO findAuthUserByUsername(String username);
    AuthUserVO findAuthUserById(Long id);
    List<String> getRoleCodes(Long userId);
    void recordLoginInfo(Long userId);
}
