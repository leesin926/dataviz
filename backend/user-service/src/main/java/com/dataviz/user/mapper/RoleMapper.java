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

    /** 角色当前的授权 id：授权接口是全量覆盖，界面改之前必须能读回原值，否则"保存"就是在清空没显示的那部分 */
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 角色当前还挂在哪些有效账号上：删角色前的引用完整性判据（绑定关系本身没有逻辑删除列，靠 join 用户表过滤） */
    long countMembersByRoleId(@Param("roleId") Long roleId);
}
