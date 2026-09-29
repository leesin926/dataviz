package com.dataviz.user.service;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.vo.PermissionTreeVO;
import com.dataviz.user.vo.RoleVO;
import java.util.List;

public interface RoleService {
    Long createRole(RoleVO roleVO, Long tenantId);
    // 下面带 tenantId 的五条一律不可省：路径上的 id 是"谁都能猜"的输入，只有请求头里的租户是网关从 JWT 换出来的。
    // 少了这个参数，update/delete/getById/assignPermissions/getRolePermissionIds 就都是跨租户读写（API-40）。
    void updateRole(Long id, RoleVO roleVO, Long tenantId);
    void deleteRole(Long id, Long tenantId);
    RoleVO getRoleById(Long id, Long tenantId);
    List<RoleVO> listRoles(Long tenantId);
    PageResult<RoleVO> pageRoles(int page, int size, Long tenantId);
    void assignPermissions(Long roleId, List<Long> permissionIds, Long tenantId);
    // 全量覆盖语义（先删后插）⇒ 界面必须先回显再改，否则"保存"就是在清空没显示出来的那部分授权。
    List<Long> getRolePermissionIds(Long roleId, Long tenantId);
    List<PermissionTreeVO> getPermissionTree();
    Long createPermission(SysPermission permission);
    void updatePermission(Long id, SysPermission permission);
    void deletePermission(Long id);
}
