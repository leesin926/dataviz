package com.dataviz.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.openapi.entity.OpenApiClient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpenApiClientMapper extends BaseMapper<OpenApiClient> {
}
