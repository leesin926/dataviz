package com.dataviz.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.openapi.entity.OpenApiLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenApiLogMapper extends BaseMapper<OpenApiLog> {
}
