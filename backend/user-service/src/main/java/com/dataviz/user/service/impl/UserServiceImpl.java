package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.dataviz.user.service.UserService;
import com.dataviz.user.vo.AuthUserVO;
import com.dataviz.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionEvictor sessionEvictor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateDTO dto, Long tenantId) {
        // Check username uniqueness
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername()).eq(SysUser::getTenantId, tenantId);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BizException("Username already exists");
        }

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setTenantId(tenantId);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        log.info("Created user: {}", user.getUsername());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserUpdateDTO dto, Long tenantId) {
        SysUser user = requireUserInTenant(id, tenantId);
        BeanUtils.copyProperties(dto, user);
        userMapper.updateById(user);
        // 昵称/头像存在登录态快照里，不驱逐就是"改了资料但界面上还是旧的"
        sessionEvictor.evictUsername(user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id, Long tenantId) {
        SysUser user = requireUserInTenant(id, tenantId);
        userMapper.deleteById(id);
        // 用户名必须在删除前取：逻辑删除之后按 @TableLogic 已经查不回来
        sessionEvictor.evictUsername(user.getUsername());
    }

    @Override
    public UserVO getUserById(Long id, Long tenantId) {
        SysUser user = requireUserInTenant(id, tenantId);
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoleIds(userMapper.selectRoleIdsByUserId(id));
        return vo;
    }

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO queryDTO) {
        Page<SysUser> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, queryDTO.getTenantId());
        if (StringUtils.hasText(queryDTO.getUsername())) {
            wrapper.like(SysUser::getUsername, queryDTO.getUsername());
        }
        if (StringUtils.hasText(queryDTO.getNickname())) {
            wrapper.like(SysUser::getNickname, queryDTO.getNickname());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, queryDTO.getStatus());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        List<UserVO> voList = result.getRecords().stream().map(u -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(u, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    @Override
    public void toggleStatus(Long id, Integer status, Long tenantId) {
        SysUser user = requireUserInTenant(id, tenantId);
        user.setStatus(status);
        userMapper.updateById(user);
        // 拦截器只看 Redis 里有没有快照，不查账号状态 ⇒ "停用账号"必须靠驱逐才真能踢人
        sessionEvictor.evictUsername(user.getUsername());
    }

    @Override
    public void resetPassword(Long id, String newPassword, Long tenantId) {
        SysUser user = requireUserInTenant(id, tenantId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        // 重置口令通常意味着"怀疑这个账号在别人手上"，旧会话不该继续有效
        sessionEvictor.evictUsername(user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds, Long tenantId) {
        SysUser user = requireUserInTenant(userId, tenantId);
        requireRolesInTenant(roleIds, tenantId);
        userMapper.deleteRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userMapper.insertBatchUserRoles(userId, roleIds);
        }
        sessionEvictor.evictUsername(user.getUsername());
    }

    @Override
    public List<String> getUserPermissions(Long userId) {
        return userMapper.selectPermissionCodesByUserId(userId);
    }

    @Override
    public AuthUserVO findAuthUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 用户名不跨租户唯一，且认证本来就按用户名全局查（迁移前 auth-service 的 selectByUsername 同样不带租户条件）。
        // 这里显式取 id 最小的一条：用 selectOne 命中重复行会抛 TooManyResultsException，把一次登录变成 500。
        wrapper.eq(SysUser::getUsername, username).orderByAsc(SysUser::getId).last("LIMIT 1");
        return toAuthUserVO(userMapper.selectOne(wrapper));
    }

    @Override
    public AuthUserVO findAuthUserByPhone(String phone) {
        // 空值必须当场挡掉：phone 列默认是 ''（DDL 无唯一约束），
        // 一次不带条件的查询会把"所有没填手机号的账号"当成命中，LIMIT 1 就随机登进其中一个。
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 同 findByUsername：不取唯一约束，显式按 id 升序取一条，避免 TooManyResultsException 把登录打成 500。
        wrapper.eq(SysUser::getPhone, phone).orderByAsc(SysUser::getId).last("LIMIT 1");
        return toAuthUserVO(userMapper.selectOne(wrapper));
    }

    @Override
    public AuthUserVO findAuthUserById(Long id) {
        if (id == null) {
            return null;
        }
        return toAuthUserVO(userMapper.selectById(id));
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        return userMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public void recordLoginInfo(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, userId)
                .set(SysUser::getLoginTime, now)
                .set(SysUser::getUpdateTime, now);
        if (userMapper.update(null, wrapper) == 0) {
            log.warn("登录时间未写入：用户不存在或已删除 userId={}", userId);
        }
    }

    /**
     * 不存在与"存在但不属于本租户"同一句话，配方同 {@code DeptServiceImpl.requireDeptInTenant}：
     * 分开报就让 id 变成"这个账号在别的租户里存在"的探针。/resetPassword、toggleStatus 这几条
     * 此前连这一层都没有 —— 拿到别人的 userId 就能重置他的口令。
     */
    private SysUser requireUserInTenant(Long id, Long tenantId) {
        SysUser user = userMapper.selectById(id);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BizException("User not found");
        }
        return user;
    }

    /** 挂角色只能挂自己租户里的角色，否则等于把别租户的身份体系接到本账号上。 */
    private void requireRolesInTenant(List<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysRole role = roleId == null ? null : roleMapper.selectById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId())) {
                throw new BizException("Role not found");
            }
        }
    }

    private AuthUserVO toAuthUserVO(SysUser user) {
        if (user == null) {
            return null;
        }
        AuthUserVO vo = new AuthUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
