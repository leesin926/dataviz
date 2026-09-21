package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiLogMapper;
import com.dataviz.openapi.service.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogServiceImpl implements ApiLogService {

    private final OpenApiLogMapper logMapper;

    @Override
    public void saveLog(OpenApiLog apiLog) {
        logMapper.insert(apiLog);
    }

    @Override
    public long getClientCallCount(Long clientId) {
        LambdaQueryWrapper<OpenApiLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiLog::getClientId, clientId);
        return logMapper.selectCount(wrapper);
    }
}
