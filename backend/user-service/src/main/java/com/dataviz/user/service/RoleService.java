package com.dataviz.user.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.vo.PermissionTreeVO;
import com.dataviz.user.vo.RoleVO;
import java.util.List;

public interface RoleService {
    Long createRole(RoleVO roleVO, Long tenantId);
    void updateRole(Long id, RoleVO roleVO);
    void deleteRole(Long id);
    RoleVO getRoleById(Long id);
    List<RoleVO> listRoles(Long tenantId);
    PageResult<RoleVO> pageRoles(int page, int size, Long tenantId);
    void assignPermissions(Long roleId, List<Long> permissionIds);
    List<PermissionTreeVO> getPermissionTree();
    Long createPermission(SysPermission permission);
    void updatePermission(Long id, SysPermission permission);
    void deletePermission(Long id);
}
