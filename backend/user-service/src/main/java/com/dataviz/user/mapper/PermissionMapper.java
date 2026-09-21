package com.dataviz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {
}
