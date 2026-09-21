package com.dataviz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Permission data access mapper.
 */
@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {

    /**
     * Find permission codes by user ID (through user_role -> role_permission join).
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
