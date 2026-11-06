package com.dataviz.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.entity.SysDept;
import com.dataviz.user.entity.SysUser;
import com.dataviz.user.mapper.DeptMapper;
import com.dataviz.user.mapper.UserMapper;
import com.dataviz.user.test.MybatisPlusTableInfoHelper;
import com.dataviz.user.vo.DeptTreeVO;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DeptServiceImpl 的写守卫：所有写操作都以 tenantId 为准（不是路径上的 id），parentId 要过归属校验，
 * 且不许把自己的子树挂到自己名下（成环会让整条环从 getDeptTree 里静默消失、看不见也删不掉）。
 * deleteDept 有子部门/有用户两道引用完整性闸。
 */
class DeptServiceImplTest {

    private DeptMapper deptMapper;
    private UserMapper userMapper;
    private DeptServiceImpl deptService;

    @BeforeAll
    static void setUpClass() {
        // deleteDept 构造 LambdaQueryWrapper<SysDept>（子部门）与 <SysUser>（部门下的人），
        // getDeptTree 构造 LambdaQueryWrapper<SysDept> —— 列名解析需要 TableInfo。
        MybatisPlusTableInfoHelper.initFor(SysDept.class, SysUser.class);
    }

    @BeforeEach
    void setUp() {
        deptMapper = mock(DeptMapper.class);
        userMapper = mock(UserMapper.class);
        deptService = new DeptServiceImpl(deptMapper, userMapper);
    }

    private SysDept dept(Long id, Long tenantId, Long parentId, String name) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setTenantId(tenantId);
        d.setParentId(parentId);
        d.setDeptName(name);
        d.setStatus(1);
        return d;
    }

    private DeptCreateDTO dto(Long parentId, String name) {
        DeptCreateDTO d = new DeptCreateDTO();
        d.setParentId(parentId);
        d.setDeptName(name);
        return d;
    }

    @Test
    @DisplayName("createDept：顶级归一成 0，名称 trim，sortOrder 缺省补 0，status=1")
    void should_normalizeRootAndTrimName_when_createDeptRoot() {
        when(deptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
            inv.<SysDept>getArgument(0).setId(7L);
            return 1;
        });

        Long id = deptService.createDept(dto(null, "  研发  "), 5L);

        assertEquals(7L, id);
        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).insert(captor.capture());
        SysDept saved = captor.getValue();
        assertEquals(0L, saved.getParentId(), "null 父级统一落成 0");
        assertEquals("研发", saved.getDeptName());
        assertEquals(0, saved.getSortOrder());
        assertEquals(1, saved.getStatus());
        assertEquals(5L, saved.getTenantId());
    }

    @Test
    @DisplayName("createDept：父级同租户且非环时挂到其下")
    void should_attachUnderParent_when_createDeptWithValidParent() {
        when(deptMapper.selectById(10L)).thenReturn(dept(10L, 5L, 0L, "总部"));
        when(deptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
            inv.<SysDept>getArgument(0).setId(11L);
            return 1;
        });

        deptService.createDept(dto(10L, "分部"), 5L);

        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getParentId());
    }

    @Test
    @DisplayName("createDept：父级属于别的租户时拒绝（跨租户挂靠本身就是数据出口）")
    void should_rejectForeignParent_when_createDeptWithParentFromOtherTenant() {
        when(deptMapper.selectById(10L)).thenReturn(dept(10L, 999L, 0L, "别人的"));

        BizException ex = assertThrows(BizException.class, () -> deptService.createDept(dto(10L, "分部"), 5L));
        assertTrue(ex.getMessage().contains("Parent department not found"));
        verify(deptMapper, never()).insert(any());
    }

    @Test
    @DisplayName("updateDept：把自己挂到子孙下 = 成环，当场拒绝")
    void should_refuseMoveUnderOwnChild_when_updateDeptCreatesCycle() {
        // 1 是 2 的父，把 1 的父改成 2 就成环：1->2->1
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 5L, 0L, "A"));
        when(deptMapper.selectById(2L)).thenReturn(dept(2L, 5L, 1L, "B"));

        BizException ex = assertThrows(BizException.class, () -> deptService.updateDept(1L, dto(2L, "A"), 5L));
        assertTrue(ex.getMessage().contains("Cannot move a department under itself"));
        verify(deptMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateDept：父级直接设成自己，requireParentUsable 首步即判环")
    void should_refuseMoveUnderSelf_when_updateDeptParentIsItself() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 5L, 0L, "A"));

        assertThrows(BizException.class, () -> deptService.updateDept(1L, dto(1L, "A"), 5L));
        verify(deptMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateDept：parentId 传 null 归一成 0（而非留 null 让节点从树里消失），sortOrder 为空才保留")
    void should_persistNormalizedParent_when_updateDept() {
        SysDept existing = dept(1L, 5L, 10L, "A");
        existing.setSortOrder(4);
        when(deptMapper.selectById(1L)).thenReturn(existing);

        DeptCreateDTO d = dto(null, " 改名 ");
        d.setLeader(null); // 显式 setter，null 会写入（与逐字段语义一致）

        deptService.updateDept(1L, d, 5L);

        ArgumentCaptor<SysDept> captor = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).updateById(captor.capture());
        SysDept saved = captor.getValue();
        assertEquals(0L, saved.getParentId(), "null 归一为 0，不能留 null");
        assertEquals("改名", saved.getDeptName());
        assertEquals(4, saved.getSortOrder(), "DTO sortOrder 为 null 时保留原值");
    }

    @Test
    @DisplayName("deleteDept：有子部门时拒绝（判据不带租户，跨租户孤儿子节点也拦）")
    void should_refuseDeleteWhenHasChildren_when_deleteDept() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 5L, 0L, "A"));
        when(deptMapper.selectCount(any())).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> deptService.deleteDept(1L, 5L));
        assertTrue(ex.getMessage().contains("Cannot delete department with children"));
        verify(deptMapper, never()).deleteById(anyLong());
        ArgumentCaptor<Wrapper<SysDept>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deptMapper).selectCount(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("parent_id"), "子部门判据按 parent_id");
    }

    @Test
    @DisplayName("deleteDept：无子部门但下面还有人时拒绝（否则用户 deptId 指向已删记录）")
    void should_refuseDeleteWhenHasUsers_when_deleteDept() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 5L, 0L, "A"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> deptService.deleteDept(1L, 5L));
        assertTrue(ex.getMessage().contains("Cannot delete department with users"));
        verify(deptMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteDept：无子无人才真正删除")
    void should_delete_when_deptClean() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 5L, 0L, "A"));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);

        deptService.deleteDept(1L, 5L);

        verify(deptMapper).deleteById(1L);
    }

    @Test
    @DisplayName("getDeptTree：只回根，子部门挂进 children")
    void should_buildTree_when_getDeptTree() {
        when(deptMapper.selectList(any())).thenReturn(Arrays.asList(
                dept(1L, 5L, 0L, "总部"), dept(2L, 5L, 1L, "研发"), dept(3L, 5L, 1L, "市场")));

        List<DeptTreeVO> tree = deptService.getDeptTree(5L);

        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).getId());
        assertEquals(2, tree.get(0).getChildren().size());
        assertFalse(tree.stream().anyMatch(v -> v.getId().equals(2L)), "子节点不该出现在顶层");
    }

    @Test
    @DisplayName("跨租户与不存在塌成同一句 Department not found（getDeptById）")
    void should_refuseCrossTenant_when_getDeptByIdOtherTenant() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 999L, 0L, "别人的"));

        BizException ex = assertThrows(BizException.class, () -> deptService.getDeptById(1L, 5L));
        assertEquals("Department not found", ex.getMessage());
    }
}
