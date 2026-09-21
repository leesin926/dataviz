package com.dataviz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.auth.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Role data access mapper.
 */
@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {

    /**
     * Find role codes by user ID.
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
