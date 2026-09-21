package com.dataviz.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.admin.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<SysTenant> {
}
