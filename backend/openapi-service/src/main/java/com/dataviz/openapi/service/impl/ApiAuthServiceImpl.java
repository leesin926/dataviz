package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.mapper.OpenApiClientMapper;
import com.dataviz.openapi.service.ApiAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiAuthServiceImpl implements ApiAuthService {

    private final OpenApiClientMapper clientMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "openapi:rate:";

    @Override
    public OpenApiClient authenticate(String appKey, String appSecret, String clientIp) {
        LambdaQueryWrapper<OpenApiClient> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiClient::getAppKey, appKey);
        OpenApiClient client = clientMapper.selectOne(wrapper);

        if (client == null) {
            throw new BizException("Invalid app key");
        }
        if (!client.getAppSecret().equals(appSecret)) {
            throw new BizException("Invalid app secret");
        }
        if (!"ACTIVE".equals(client.getStatus())) {
            throw new BizException("API client is disabled");
        }
        if (client.getExpireTime() != null && client.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("API client has expired");
        }
        if (!checkIpWhitelist(client, clientIp)) {
            throw new BizException("IP address not allowed: " + clientIp);
        }
        if (!checkRateLimit(client)) {
            throw new BizException("Rate limit exceeded");
        }
        return client;
    }

    @Override
    public boolean checkRateLimit(OpenApiClient client) {
        String key = RATE_LIMIT_KEY_PREFIX + client.getAppKey();
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;

        if (count >= client.getRateLimit()) {
            return false;
        }

        AtomicLong newCount = new AtomicLong(redisTemplate.opsForValue().increment(key));
        if (newCount.get() == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return true;
    }

    @Override
    public boolean checkIpWhitelist(OpenApiClient client, String clientIp) {
        String allowedIps = client.getAllowedIps();
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            // Empty means all IPs allowed
            return true;
        }
        List<String> ipList = Arrays.asList(allowedIps.split(","));
        return ipList.contains(clientIp) || ipList.contains("*");
    }
}
