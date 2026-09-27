package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.entity.SysRole;
import com.dataviz.user.mapper.PermissionMapper;
import com.dataviz.user.mapper.RoleMapper;
import com.dataviz.user.service.LoginSessionEvictor;
import com.dataviz.user.service.RoleService;
import com.dataviz.user.vo.PermissionTreeVO;
import com.dataviz.user.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final LoginSessionEvictor sessionEvictor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleVO roleVO, Long tenantId) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(roleVO, role);
        role.setTenantId(tenantId);
        role.setStatus(1);
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleVO roleVO, Long tenantId) {
        SysRole role = requireRoleInTenant(id, tenantId);
        // 逐字段而不是 copyProperties：RoleVO 带着 tenantId/roleCode，整拷贝等于让人把自己搬去别的租户、
        // 或把 role_code 改成 super_admin —— 后者是 PermissionInterceptor 短路放行的钥匙，改它等于自己发特权。
        role.setRoleName(roleVO.getRoleName());
        role.setDescription(roleVO.getDescription());
        if (roleVO.getSortOrder() != null) {
            role.setSortOrder(roleVO.getSortOrder());
        }
        if (roleVO.getStatus() != null) {
            role.setStatus(roleVO.getStatus());
        }
        roleMapper.updateById(role);
        // 改 role_code 或把角色停用，都会改变成员该有哪些角色/权限（取码 SQL 带 status=1 AND deleted=0 条件）
        sessionEvictor.evictRoleMembers(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id, Long tenantId) {
        requireRoleInTenant(id, tenantId);
        if (roleMapper.countMembersByRoleId(id) > 0) {
            throw new BizException("Cannot delete role assigned to users");
        }
        roleMapper.deleteById(id);
        sessionEvictor.evictRoleMembers(id);
    }

    @Override
    public RoleVO getRoleById(Long id, Long tenantId) {
        SysRole role = requireRoleInTenant(id, tenantId);
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    @Override
    public List<RoleVO> listRoles(Long tenantId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getTenantId, tenantId).orderByAsc(SysRole::getSortOrder);
        return roleMapper.selectList(wrapper).stream().map(r -> {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<RoleVO> pageRoles(int page, int size, Long tenantId) {
        Page<SysRole> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getTenantId, tenantId).orderByAsc(SysRole::getSortOrder);
        Page<SysRole> result = roleMapper.selectPage(pageObj, wrapper);
        List<RoleVO> voList = result.getRecords().stream().map(r -> {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(voList, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds, Long tenantId) {
        requireRoleInTenant(roleId, tenantId);
        roleMapper.deletePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            roleMapper.insertBatchRolePermissions(roleId, permissionIds);
        }
        // 授权改的是 Redis 快照的"上游"，快照本身不会自己变 ⇒ 不驱逐就等用户下次登录才生效
        sessionEvictor.evictRoleMembers(roleId);
    }

    @Override
    public List<PermissionTreeVO> getPermissionTree() {
        List<SysPermission> all = permissionMapper.selectList(null);
        List<PermissionTreeVO> voList = all.stream().map(p -> {
            PermissionTreeVO vo = new PermissionTreeVO();
            BeanUtils.copyProperties(p, vo);
            return vo;
        }).collect(Collectors.toList());

        Map<Long, List<PermissionTreeVO>> childrenMap = voList.stream()
                .filter(v -> v.getParentId() != null && v.getParentId() != 0)
                .collect(Collectors.groupingBy(PermissionTreeVO::getParentId));

        voList.forEach(v -> v.setChildren(childrenMap.getOrDefault(v.getId(), new ArrayList<>())));
        return voList.stream().filter(v -> v.getParentId() == null || v.getParentId() == 0).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermission(SysPermission permission) {
        permission.setStatus(1);
        permissionMapper.insert(permission);
        return permission.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(Long id, SysPermission permission) {
        permission.setId(id);
        permissionMapper.updateById(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        permissionMapper.deleteById(id);
    }

    /**
     * 不存在与"存在但不属于本租户"必须是同一句话：分开报就把 404 变成了"这个角色 id 属于别人"的探针。
     * 与 {@code DeptServiceImpl.requireDeptInTenant} 同一条配方。
     */
    private SysRole requireRoleInTenant(Long id, Long tenantId) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || !tenantId.equals(role.getTenantId())) {
            throw new BizException("Role not found");
        }
        return role;
    }
}
