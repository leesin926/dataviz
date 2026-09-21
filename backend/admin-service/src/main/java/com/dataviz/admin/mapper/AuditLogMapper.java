package com.dataviz.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.admin.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
