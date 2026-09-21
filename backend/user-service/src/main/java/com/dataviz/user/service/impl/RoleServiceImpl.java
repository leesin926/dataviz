package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.entity.SysRole;
import com.dataviz.user.mapper.PermissionMapper;
import com.dataviz.user.mapper.RoleMapper;
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
    public void updateRole(Long id, RoleVO roleVO) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new BizException("Role not found");
        BeanUtils.copyProperties(roleVO, role);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public RoleVO getRoleById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new BizException("Role not found");
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
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        roleMapper.deletePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            roleMapper.insertBatchRolePermissions(roleId, permissionIds);
        }
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
}
