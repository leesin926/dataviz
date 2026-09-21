package com.dataviz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.auth.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Login log data access mapper.
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<SysLoginLog> {
}
