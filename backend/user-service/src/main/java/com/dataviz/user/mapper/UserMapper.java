package com.dataviz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
    void deleteRolesByUserId(@Param("userId") Long userId);
    void insertBatchUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
    List<String> selectUsernamesByRoleId(@Param("roleId") Long roleId);
}
