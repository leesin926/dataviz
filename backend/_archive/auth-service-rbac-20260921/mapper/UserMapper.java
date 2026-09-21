package com.dataviz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * User data access mapper.
 */
@Mapper
public interface UserMapper extends BaseMapper<SysUser> {

    /**
     * Find user by username.
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * Update last login info.
     */
    void updateLoginInfo(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);
}
