package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.user.dto.UserCreateDTO;
import com.dataviz.user.dto.UserQueryDTO;
import com.dataviz.user.dto.UserUpdateDTO;
import com.dataviz.user.entity.SysUser;
import com.dataviz.user.mapper.UserMapper;
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
    private final PasswordEncoder passwordEncoder;

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
    public void updateUser(Long id, UserUpdateDTO dto) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("User not found");
        }
        BeanUtils.copyProperties(dto, user);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException("User not found");
        }
        userMapper.deleteById(id);
    }

    @Override
    public UserVO getUserById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("User not found");
        }
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
    public void toggleStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("User not found");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("User not found");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userMapper.deleteRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userMapper.insertBatchUserRoles(userId, roleIds);
        }
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

    private AuthUserVO toAuthUserVO(SysUser user) {
        if (user == null) {
            return null;
        }
        AuthUserVO vo = new AuthUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
