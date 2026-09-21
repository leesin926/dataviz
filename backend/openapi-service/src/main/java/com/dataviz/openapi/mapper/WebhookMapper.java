package com.dataviz.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.openapi.entity.OpenApiWebhook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookMapper extends BaseMapper<OpenApiWebhook> {
}
