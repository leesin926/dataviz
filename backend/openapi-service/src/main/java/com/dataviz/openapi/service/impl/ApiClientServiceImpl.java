package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.openapi.dto.ApiKeyCreateDTO;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.mapper.OpenApiClientMapper;
import com.dataviz.openapi.service.ApiClientService;
import com.dataviz.openapi.vo.ApiClientVO;
import com.dataviz.openapi.vo.ApiKeyVO;
import com.dataviz.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClientServiceImpl implements ApiClientService {

    private final OpenApiClientMapper clientMapper;

    @Override
    @Transactional
    public ApiKeyVO generateKey(ApiKeyCreateDTO dto) {
        String appKey = generateAppKey();
        String appSecret = generateAppSecret();

        OpenApiClient client = OpenApiClient.builder()
                .appName(dto.getAppName())
                .appKey(appKey)
                .appSecret(appSecret)
                .status("ACTIVE")
                .rateLimit(dto.getRateLimit() != null ? dto.getRateLimit() : 60)
                .allowedIps(dto.getAllowedIps() != null ? String.join(",", dto.getAllowedIps()) : "")
                .expireTime(dto.getExpireTime())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        clientMapper.insert(client);
        log.info("Generated API key: clientId={}, appName={}, appKey={}", client.getId(), client.getAppName(), appKey);

        return ApiKeyVO.builder()
                .clientId(client.getId())
                .appName(client.getAppName())
                .appKey(appKey)
                .appSecret(appSecret)
                .build();
    }

    @Override
    @Transactional
    public ApiKeyVO resetSecret(Long clientId) {
        OpenApiClient client = clientMapper.selectById(clientId);
        if (client == null) {
            throw new BizException("API client not found: " + clientId);
        }
        String newSecret = generateAppSecret();
        client.setAppSecret(newSecret);
        client.setUpdateTime(LocalDateTime.now());
        clientMapper.updateById(client);
        log.info("Reset API secret: clientId={}, appName={}", client.getId(), client.getAppName());

        return ApiKeyVO.builder()
                .clientId(client.getId())
                .appName(client.getAppName())
                .appKey(client.getAppKey())
                .appSecret(newSecret)
                .build();
    }

    @Override
    public List<ApiClientVO> listClients() {
        LambdaQueryWrapper<OpenApiClient> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OpenApiClient::getCreateTime);
        return clientMapper.selectList(wrapper).stream()
                .map(this::toClientVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void disableClient(Long clientId) {
        OpenApiClient client = clientMapper.selectById(clientId);
        if (client == null) {
            throw new BizException("API client not found: " + clientId);
        }
        client.setStatus("DISABLED");
        client.setUpdateTime(LocalDateTime.now());
        clientMapper.updateById(client);
        log.info("Disabled API client: clientId={}, appName={}", client.getId(), client.getAppName());
    }

    private ApiClientVO toClientVO(OpenApiClient client) {
        ApiClientVO vo = new ApiClientVO();
        BeanUtils.copyProperties(client, vo);
        // Never return appSecret in list
        return vo;
    }

    /**
     * Generate app key using UUID (remove hyphens for shorter key)
     */
    private String generateAppKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate app secret using double UUID for stronger security
     */
    private String generateAppSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
