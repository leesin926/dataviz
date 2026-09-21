package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.openapi.dto.AppCreateDTO;
import com.dataviz.openapi.entity.OpenApiApp;
import com.dataviz.openapi.mapper.OpenApiAppMapper;
import com.dataviz.openapi.service.OpenApiAppService;
import com.dataviz.openapi.vo.AppVO;
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
public class OpenApiAppServiceImpl implements OpenApiAppService {

    private final OpenApiAppMapper openApiAppMapper;

    @Override
    @Transactional
    public Long create(AppCreateDTO dto) {
        OpenApiApp app = new OpenApiApp();
        BeanUtils.copyProperties(dto, app);
        app.setAppKey(generateAppKey());
        app.setAppSecret(generateAppSecret());
        app.setStatus(1);
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        openApiAppMapper.insert(app);
        log.info("Created OpenAPI app: id={}, name={}", app.getId(), app.getAppName());
        return app.getId();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        openApiAppMapper.deleteById(id);
        log.info("Deleted OpenAPI app: id={}", id);
    }

    @Override
    public AppVO getById(Long id) {
        OpenApiApp app = openApiAppMapper.selectById(id);
        if (app == null) {
            throw new RuntimeException("OpenAPI app not found: " + id);
        }
        AppVO vo = new AppVO();
        BeanUtils.copyProperties(app, vo);
        return vo;
    }

    @Override
    public List<AppVO> list(Long tenantId) {
        LambdaQueryWrapper<OpenApiApp> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(OpenApiApp::getTenantId, tenantId);
        }
        wrapper.orderByDesc(OpenApiApp::getCreateTime);
        List<OpenApiApp> list = openApiAppMapper.selectList(wrapper);
        return list.stream().map(a -> {
            AppVO vo = new AppVO();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppVO regenerateSecret(Long id) {
        OpenApiApp app = openApiAppMapper.selectById(id);
        if (app == null) {
            throw new RuntimeException("OpenAPI app not found: " + id);
        }
        app.setAppSecret(generateAppSecret());
        app.setUpdateTime(LocalDateTime.now());
        openApiAppMapper.updateById(app);
        AppVO vo = new AppVO();
        BeanUtils.copyProperties(app, vo);
        log.info("Regenerated secret for app: id={}", id);
        return vo;
    }

    private String generateAppKey() {
        return "ak_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateAppSecret() {
        return "sk_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
