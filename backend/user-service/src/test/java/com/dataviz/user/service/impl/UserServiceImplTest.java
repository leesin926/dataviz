package com.dataviz.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.dto.UserCreateDTO;
import com.dataviz.user.dto.UserQueryDTO;
import com.dataviz.user.dto.UserUpdateDTO;
import com.dataviz.user.entity.SysRole;
import com.dataviz.user.entity.SysUser;
import com.dataviz.user.mapper.RoleMapper;
import com.dataviz.user.mapper.UserMapper;
import com.dataviz.user.service.LoginSessionEvictor;
import com.dataviz.user.test.MybatisPlusTableInfoHelper;
import com.dataviz.user.vo.AuthUserVO;
import com.dataviz.user.vo.UserVO;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * UserServiceImpl 的决策路径。重点钉三件事：
 * 1) 用户名唯一性只在<strong>租户内</strong>判定（captured Wrapper 同时带 username 与 tenant_id）；
 * 2) 口令是编码后入库、绝不存明文；
 * 3) findAuthUserBy* 是喂给 auth-service 的入口——username/phone 无租户条件且 LIMIT 1
 *    （API-40 修复：用 selectOne 命中重复行会抛 TooManyResultsException 把登录打成 500），
 *    空值必须当场挡掉。跨租户写操作统一塌成同一句 "User not found"，不给枚举探针。
 */
class UserServiceImplTest {

    private UserMapper userMapper;
    private RoleMapper roleMapper;
    private PasswordEncoder passwordEncoder;
    private LoginSessionEvictor sessionEvictor;
    private UserServiceImpl userService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(SysUser.class, SysRole.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        roleMapper = mock(RoleMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionEvictor = mock(LoginSessionEvictor.class);
        userService = new UserServiceImpl(userMapper, roleMapper, passwordEncoder, sessionEvictor);
    }

    private SysUser user(Long id, Long tenantId, String username) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setPassword("$2a$old");
        u.setNickname("旧昵称");
        u.setEmail("old@x.com");
        u.setPhone("13800000000");
        u.setStatus(1);
        return u;
    }

    // ==================== createUser ====================

    @Test
    @DisplayName("createUser：重复判定按 username + tenant_id 双条件（仅租户内唯一）")
    void should_scopeDuplicateCheckToTenant_when_createUser() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("alice");
        dto.setPassword("plain");
        when(userMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> userService.createUser(dto, 5L));
        assertTrue(ex.getMessage().contains("Username already exists"));

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectCount(captor.capture());
        Wrapper<SysUser> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        Map<String, Object> pairs = ((AbstractWrapper<SysUser, ?, ?>) wrapper).getParamNameValuePairs();
        assertTrue(sql.contains("username ="), "重复校验应含 username: " + sql);
        assertTrue(sql.contains("tenant_id ="), "重复校验应含 tenant_id: " + sql);
        assertTrue(pairs.containsValue("alice"));
        assertTrue(pairs.containsValue(5L), "跨租户同名应允许，故判据必须带租户: " + pairs);
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createUser：口令编码后入库、状态置 1，返回自增 id")
    void should_encodePasswordAndDefaultStatus_when_createUser() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("bob");
        dto.setPassword("plain-pw");
        dto.setNickname("Bob");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("plain-pw")).thenReturn("$2a$ENCODED");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            inv.<SysUser>getArgument(0).setId(42L);
            return 1;
        });

        Long id = userService.createUser(dto, 7L);

        assertEquals(42L, id);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("$2a$ENCODED", saved.getPassword());
        assertFalse("plain-pw".equals(saved.getPassword()), "绝不存明文口令");
        assertEquals(1, saved.getStatus());
        assertEquals(7L, saved.getTenantId());
        assertEquals("bob", saved.getUsername());
        assertEquals("Bob", saved.getNickname());
    }

    // ==================== updateUser ====================

    @Test
    @DisplayName("updateUser：未知 id 抛 User not found，不写库不驱逐")
    void should_throwNotFound_when_updateUserUnknownId() {
        when(userMapper.selectById(404L)).thenReturn(null);
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setNickname("x");

        assertThrows(BizException.class, () -> userService.updateUser(404L, dto, 5L));
        verify(userMapper, never()).updateById(any());
        verify(sessionEvictor, never()).evictUsername(anyString());
    }

    @Test
    @DisplayName("updateUser：跨租户与不存在塌成同一句话，拒绝改写他人账号")
    void should_refuseCrossTenant_when_updateUserOtherTenant() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 999L, "victim"));
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setNickname("hacked");

        BizException ex = assertThrows(BizException.class, () -> userService.updateUser(1L, dto, 5L));
        assertEquals("User not found", ex.getMessage());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateUser：copyProperties 语义——DTO 传 null 即清空该可选字段")
    void should_clearOptionalFields_when_updateUserOmitsThem() {
        SysUser existing = user(1L, 5L, "carol");
        when(userMapper.selectById(1L)).thenReturn(existing);
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setNickname("NewNick");
        // email/phone/avatar/deptId 不传 → null

        userService.updateUser(1L, dto, 5L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("NewNick", saved.getNickname());
        assertNull(saved.getEmail(), "copyProperties 把 DTO 缺省字段抹成 null（清空语义）");
        assertNull(saved.getPhone());
        // username/tenant/password 不在 DTO 里，保留原值
        assertEquals("carol", saved.getUsername());
        assertEquals(5L, saved.getTenantId());
        verify(sessionEvictor).evictUsername("carol");
    }

    // ==================== deleteUser ====================

    @Test
    @DisplayName("deleteUser：本租户删除后按用户名驱逐会话")
    void should_deleteAndEvictByUsername_when_deleteUser() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 5L, "dave"));

        userService.deleteUser(1L, 5L);

        verify(userMapper).deleteById(1L);
        verify(sessionEvictor).evictUsername("dave");
    }

    @Test
    @DisplayName("deleteUser：跨租户直接拒绝，不删他人账号")
    void should_refuseCrossTenant_when_deleteUserOtherTenant() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 999L, "victim"));

        assertThrows(BizException.class, () -> userService.deleteUser(1L, 5L));
        verify(userMapper, never()).deleteById(anyLong());
        verify(sessionEvictor, never()).evictUsername(anyString());
    }

    // ==================== listUsers ====================

    @Test
    @DisplayName("listUsers：租户 + username/nickname/status 过滤与排序进同一条 Wrapper")
    void should_applyAllFilters_when_listUsersWithQuery() {
        UserQueryDTO q = new UserQueryDTO();
        q.setTenantId(5L);
        q.setUsername("ali");
        q.setNickname("Al");
        q.setStatus(1);
        Page<SysUser> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Collections.singletonList(user(1L, 5L, "alice")));
        pageResult.setTotal(1L);
        when(userMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<UserVO> result = userService.listUsers(q);

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectPage(any(), captor.capture());
        Wrapper<SysUser> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        Map<String, Object> pairs = ((AbstractWrapper<SysUser, ?, ?>) wrapper).getParamNameValuePairs();
        assertTrue(sql.contains("tenant_id"), sql);
        assertTrue(sql.contains("LIKE"), "username/nickname 走模糊匹配: " + sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("ORDER BY"), sql);
        assertTrue(pairs.containsValue(5L));
        assertEquals(1L, result.getTotal());
        assertEquals("alice", result.getRecords().get(0).getUsername());
    }

    @Test
    @DisplayName("listUsers：只带租户时不下 username/nickname/status 条件")
    void should_filterOnlyTenant_when_listUsersWithoutOptionalCriteria() {
        UserQueryDTO q = new UserQueryDTO();
        q.setTenantId(5L);
        Page<SysUser> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Collections.<SysUser>emptyList());
        pageResult.setTotal(0L);
        when(userMapper.selectPage(any(), any())).thenReturn(pageResult);

        userService.listUsers(q);

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"), sql);
        assertFalse(sql.contains("status"), "未传 status 不应拼该条件: " + sql);
        assertFalse(sql.contains("LIKE"), "未传 username/nickname 不应拼 LIKE: " + sql);
    }

    // ==================== toggleStatus / resetPassword ====================

    @Test
    @DisplayName("toggleStatus：改状态并驱逐会话（拦截器只看快照，不驱逐就踢不掉人）")
    void should_setStatusAndEvict_when_toggleStatus() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 5L, "erin"));

        userService.toggleStatus(1L, 0, 5L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(sessionEvictor).evictUsername("erin");
    }

    @Test
    @DisplayName("resetPassword：编码新口令入库并驱逐旧会话，encode 入参是新明文不是旧哈希")
    void should_encodeNewPasswordAndEvict_when_resetPassword() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 5L, "frank"));
        when(passwordEncoder.encode("fresh")).thenReturn("$2b$FRESH");

        userService.resetPassword(1L, "fresh", 5L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("$2b$FRESH", captor.getValue().getPassword());
        verify(passwordEncoder).encode("fresh");
        verify(passwordEncoder, never()).encode("$2a$old");
        verify(sessionEvictor).evictUsername("frank");
    }

    // ==================== findAuthUserBy* ====================

    @Test
    @DisplayName("findAuthUserByUsername：空白用户名直接返回 null，绝不发无条件查询")
    void should_returnNull_when_findAuthUserByUsernameBlank() {
        assertNull(userService.findAuthUserByUsername(""));
        assertNull(userService.findAuthUserByUsername(null));
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("findAuthUserByUsername：按 username 过滤 + ORDER BY id + LIMIT 1，且不注入租户条件（全局登录查找）")
    void should_useLimitOneWithoutTenant_when_findAuthUserByUsername() {
        when(userMapper.selectOne(any())).thenReturn(user(9L, 5L, "grace"));

        AuthUserVO vo = userService.findAuthUserByUsername("grace");

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(captor.capture());
        Wrapper<SysUser> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("username ="), sql);
        assertTrue(sql.contains("ORDER BY"), "按 id 升序取一条避免 TooManyResults: " + sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
        assertFalse(sql.contains("tenant_id"), "登录查找按 username 全局命中，不带租户: " + sql);
        assertEquals(9L, vo.getId());
        assertEquals("grace", vo.getUsername());
        assertEquals("$2a$old", vo.getPassword(), "认证视图必须带哈希口令供 auth 侧 matches");
        assertEquals(1, vo.getStatus());
    }

    @Test
    @DisplayName("findAuthUserByPhone：空手机号必须当场挡（phone 列默认 '' 会误命中全体无手机号账号）")
    void should_returnNull_when_findAuthUserByPhoneBlank() {
        assertNull(userService.findAuthUserByPhone(""));
        assertNull(userService.findAuthUserByPhone(null));
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("findAuthUserByPhone：非空时按 phone + LIMIT 1 查询")
    void should_useLimitOne_when_findAuthUserByPhoneProvided() {
        when(userMapper.selectOne(any())).thenReturn(user(11L, 5L, "henry"));

        AuthUserVO vo = userService.findAuthUserByPhone("13900000000");

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("phone ="), sql);
        assertTrue(sql.contains("LIMIT 1"), sql);
        assertEquals(11L, vo.getId());
    }

    @Test
    @DisplayName("findAuthUserById：null 直接返回不查库；否则走 selectById（无服务层租户判据，靠运行时拦截器兜底）")
    void should_delegateToSelectById_when_findAuthUserById() {
        assertNull(userService.findAuthUserById(null));
        verify(userMapper, never()).selectById(any());

        when(userMapper.selectById(12L)).thenReturn(user(12L, 5L, "iris"));
        AuthUserVO vo = userService.findAuthUserById(12L);
        assertEquals(12L, vo.getId());
        assertEquals("iris", vo.getUsername());
    }

    @Test
    @DisplayName("findAuthUserBy*：查库无结果时返回 null 而不是抛")
    void should_returnNull_when_findAuthUserByUsernameNoMatch() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertNull(userService.findAuthUserByUsername("ghost"));
    }

    // ==================== assignRoles ====================

    @Test
    @DisplayName("assignRoles：角色同租户则全量重写绑定并驱逐")
    void should_rewriteBindings_when_assignRolesSameTenant() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 5L, "jack"));
        when(roleMapper.selectById(any())).thenReturn(inTenantRole(2L, 5L));

        userService.assignRoles(1L, Arrays.asList(2L, 3L), 5L);

        verify(userMapper).deleteRolesByUserId(1L);
        verify(userMapper).insertBatchUserRoles(1L, Arrays.asList(2L, 3L));
        verify(sessionEvictor).evictUsername("jack");
    }

    @Test
    @DisplayName("assignRoles：混入他租户角色直接拒绝，不动现有绑定")
    void should_rejectForeignRole_when_assignRoles() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 5L, "jack"));
        when(roleMapper.selectById(3L)).thenReturn(inTenantRole(3L, 999L));

        assertThrows(BizException.class, () -> userService.assignRoles(1L, Arrays.asList(2L, 3L), 5L));
        verify(userMapper, never()).deleteRolesByUserId(any());
        verify(userMapper, never()).insertBatchUserRoles(any(), any());
    }

    // ==================== recordLoginInfo ====================

    @Test
    @DisplayName("recordLoginInfo：用 LambdaUpdateWrapper 写 login_time/update_time，命中 0 行也不抛")
    void should_buildUpdateWrapper_when_recordLoginInfo() {
        when(userMapper.update(any(), any())).thenReturn(0);

        userService.recordLoginInfo(1L);

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper, times(1)).update(any(), captor.capture());
        LambdaUpdateWrapper<SysUser> update = (LambdaUpdateWrapper<SysUser>) captor.getValue();
        // login_time/update_time 落在 SET 段，id 条件落在 WHERE 段（getSqlSegment 只给 WHERE）
        assertTrue(update.getSqlSet().contains("login_time"), update.getSqlSet());
        assertTrue(update.getSqlSet().contains("update_time"), update.getSqlSet());
        assertTrue(captor.getValue().getSqlSegment().contains("id ="), captor.getValue().getSqlSegment());
    }

    private SysRole inTenantRole(Long id, Long tenantId) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setTenantId(tenantId);
        return r;
    }
}
