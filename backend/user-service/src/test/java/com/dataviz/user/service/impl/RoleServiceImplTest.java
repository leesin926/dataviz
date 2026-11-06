package com.dataviz.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.user.entity.SysPermission;
import com.dataviz.user.entity.SysRole;
import com.dataviz.user.mapper.PermissionMapper;
import com.dataviz.user.mapper.RoleMapper;
import com.dataviz.user.service.LoginSessionEvictor;
import com.dataviz.user.vo.PermissionTreeVO;
import com.dataviz.user.vo.RoleVO;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RoleServiceImpl 的守卫路径。核心是 updateRole 的"逐字段而不是 copyProperties"：
 * RoleVO 带着 tenantId/roleCode，整拷贝等于让人把自己搬去别的租户、或把 role_code 改成
 * super_admin（PermissionInterceptor 短路的钥匙）。这里把这两条特权出口钉死。
 * 另外钉：删角色前的成员引用完整性、授权列表的存在性校验与去重。
 * 本类的所有方法都不构造 lambda Wrapper（走 selectById/count/自定义 mapper），故不依赖 TableInfo。
 */
class RoleServiceImplTest {

    private RoleMapper roleMapper;
    private PermissionMapper permissionMapper;
    private LoginSessionEvictor sessionEvictor;
    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        permissionMapper = mock(PermissionMapper.class);
        sessionEvictor = mock(LoginSessionEvictor.class);
        roleService = new RoleServiceImpl(roleMapper, permissionMapper, sessionEvictor);
    }

    private SysRole role(Long id, Long tenantId, String code) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setRoleCode(code);
        r.setRoleName("旧名");
        r.setSortOrder(3);
        r.setStatus(1);
        return r;
    }

    @Test
    @DisplayName("createRole：租户以入参为准（覆盖 VO 自带值），状态强制 1")
    void should_forceTenantAndStatus_when_createRole() {
        RoleVO vo = new RoleVO();
        vo.setRoleCode("analyst");
        vo.setRoleName("分析员");
        vo.setTenantId(999L); // 恶意/失误携带的租户，必须被入参覆盖
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
            inv.<SysRole>getArgument(0).setId(20L);
            return 1;
        });

        Long id = roleService.createRole(vo, 5L);

        assertEquals(20L, id);
        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().getTenantId());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals("analyst", captor.getValue().getRoleCode());
    }

    @Test
    @DisplayName("updateRole：不回填 roleCode/tenantId（否则等于自授 super_admin 特权或搬到他人租户）")
    void should_notTouchRoleCodeOrTenant_when_updateRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        RoleVO vo = new RoleVO();
        vo.setRoleCode("super_admin"); // 想篡改角色码
        vo.setTenantId(999L);          // 想搬到别的租户
        vo.setRoleName("改名");
        vo.setDescription("desc");
        vo.setSortOrder(9);
        vo.setStatus(0);

        roleService.updateRole(1L, vo, 5L);

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).updateById(captor.capture());
        SysRole saved = captor.getValue();
        assertEquals("analyst", saved.getRoleCode(), "roleCode 不该被 VO 覆盖");
        assertEquals(5L, saved.getTenantId(), "tenantId 不该被 VO 覆盖");
        assertEquals("改名", saved.getRoleName());
        assertEquals(9, saved.getSortOrder());
        assertEquals(0, saved.getStatus());
        verify(sessionEvictor).evictRoleMembers(1L);
    }

    @Test
    @DisplayName("updateRole：sortOrder/status 传 null 时保留原值（只有显式非空才改）")
    void should_keepExistingSortAndStatus_when_updateRoleFieldsNull() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        RoleVO vo = new RoleVO();
        vo.setRoleName("只改名");
        // sortOrder / status 不传 → null

        roleService.updateRole(1L, vo, 5L);

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getSortOrder());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateRole：跨租户与不存在塌成同一句 Role not found")
    void should_refuseCrossTenant_when_updateRoleOtherTenant() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 999L, "x"));

        BizException ex = assertThrows(BizException.class, () -> roleService.updateRole(1L, new RoleVO(), 5L));
        assertEquals("Role not found", ex.getMessage());
        verify(roleMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("deleteRole：仍有成员时拒绝删除，避免留下悬空的用户-角色绑定")
    void should_refuseDelete_when_roleHasMembers() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        when(roleMapper.countMembersByRoleId(1L)).thenReturn(3L);

        BizException ex = assertThrows(BizException.class, () -> roleService.deleteRole(1L, 5L));
        assertTrue(ex.getMessage().contains("Cannot delete role assigned to users"));
        verify(roleMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteRole：无成员才删除并驱逐该角色成员会话")
    void should_deleteAndEvict_when_roleHasNoMembers() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        when(roleMapper.countMembersByRoleId(1L)).thenReturn(0L);

        roleService.deleteRole(1L, 5L);

        verify(roleMapper).deleteById(1L);
        verify(sessionEvictor).evictRoleMembers(1L);
    }

    @Test
    @DisplayName("assignPermissions：授权列表含不存在的权限项时拒绝，不安静写坏引用")
    void should_rejectUnknownPermissions_when_assignPermissions() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        // 去重后请求 2 个 id，但库里只查到 1 个
        when(permissionMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(new SysPermission()));

        BizException ex = assertThrows(BizException.class,
                () -> roleService.assignPermissions(1L, Arrays.asList(10L, 20L), 5L));
        assertTrue(ex.getMessage().contains("不存在或已删除"));
        verify(roleMapper, never()).insertBatchRolePermissions(any(), any());
    }

    @Test
    @DisplayName("assignPermissions：去 null 去重后全量覆盖并驱逐（重复 id 会撞唯一键，500 无意义）")
    void should_normalizeDistinctNonNull_when_assignPermissions() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 5L, "analyst"));
        when(permissionMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(perm(10L), perm(20L)));

        roleService.assignPermissions(1L, Arrays.asList(10L, 10L, null, 20L), 5L);

        verify(roleMapper).deletePermissionsByRoleId(1L);
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleMapper).insertBatchRolePermissions(eq(1L), captor.capture());
        assertEquals(Arrays.asList(10L, 20L), captor.getValue());
        verify(sessionEvictor).evictRoleMembers(1L);
    }

    @Test
    @DisplayName("getPermissionTree：只回根节点，子节点挂到 children 上")
    void should_returnOnlyRootsWithChildren_when_getPermissionTree() {
        // 1L 的 parentId 为 null → 根；2L/3L 挂到 1 下
        when(permissionMapper.selectList(null)).thenReturn(Arrays.asList(
                perm(1L), permParent(2L, 1L), permParent(3L, 1L)));

        List<PermissionTreeVO> tree = roleService.getPermissionTree();

        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).getId());
        assertEquals(2, tree.get(0).getChildren().size());
        assertFalse(tree.stream().anyMatch(v -> v.getId().equals(2L) || v.getId().equals(3L)),
                "非根不应出现在顶层");
    }

    private SysPermission perm(Long id) {
        SysPermission p = new SysPermission();
        p.setId(id);
        return p;
    }

    private SysPermission permParent(Long id, Long parentId) {
        SysPermission p = new SysPermission();
        p.setId(id);
        p.setParentId(parentId);
        return p;
    }
}
