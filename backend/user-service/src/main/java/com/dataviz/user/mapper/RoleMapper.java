package com.dataviz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.user.entity.SysRole;
import com.dataviz.user.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {
    void deletePermissionsByRoleId(@Param("roleId") Long roleId);
    void insertBatchRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
