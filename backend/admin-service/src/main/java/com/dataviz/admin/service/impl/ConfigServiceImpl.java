package com.dataviz.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.admin.dto.ConfigCreateDTO;
import com.dataviz.admin.entity.SysConfig;
import com.dataviz.admin.mapper.SysConfigMapper;
import com.dataviz.admin.service.ConfigService;
import com.dataviz.admin.vo.ConfigVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String CONFIG_CACHE_PREFIX = "sys:config:";
    private static final long CACHE_TTL_MINUTES = 60;

    @Override
    @Transactional
    public Long create(ConfigCreateDTO dto) {
        SysConfig config = new SysConfig();
        BeanUtils.copyProperties(dto, config);
        config.setConfigType(StringUtils.hasText(dto.getConfigType()) ? dto.getConfigType() : "CUSTOM");
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        configMapper.insert(config);
        // Invalidate cache for this key
        evictCache(dto.getConfigKey());
        log.info("Created config: id={}, key={}", config.getId(), config.getConfigKey());
        return config.getId();
    }

    @Override
    @Transactional
    public void update(ConfigCreateDTO dto) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, dto.getConfigKey());
        SysConfig config = configMapper.selectOne(wrapper);
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Config not found: " + dto.getConfigKey());
        }
        config.setConfigValue(dto.getConfigValue());
        if (StringUtils.hasText(dto.getConfigType())) {
            config.setConfigType(dto.getConfigType());
        }
        if (dto.getRemark() != null) {
            config.setRemark(dto.getRemark());
        }
        config.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(config);
        // Invalidate cache for this key
        evictCache(dto.getConfigKey());
        log.info("Updated config: id={}, key={}", config.getId(), config.getConfigKey());
    }

    @Override
    public ConfigVO getById(Long id) {
        SysConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Config not found: " + id);
        }
        return toVO(config);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysConfig config = configMapper.selectById(id);
        if (config != null) {
            evictCache(config.getConfigKey());
        }
        configMapper.deleteById(id);
        log.info("Deleted config: id={}", id);
    }

    @Override
    public PageResult<ConfigVO> page(PageQuery pageQuery, String keyword, String configType) {
        Page<SysConfig> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysConfig::getConfigKey, keyword).or().like(SysConfig::getConfigValue, keyword));
        }
        if (StringUtils.hasText(configType)) {
            wrapper.eq(SysConfig::getConfigType, configType);
        }
        wrapper.orderByDesc(SysConfig::getCreateTime);
        Page<SysConfig> result = configMapper.selectPage(page, wrapper);
        List<ConfigVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public String getByKey(String configKey) {
        // Try cache first
        String cacheKey = CONFIG_CACHE_PREFIX + configKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Config cache hit: key={}", configKey);
            return cached;
        }

        // Query from DB
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig config = configMapper.selectOne(wrapper);
        if (config == null) {
            return null;
        }

        // Cache the value（空值不写缓存，StringRedisTemplate 拒绝 null）
        if (config.getConfigValue() != null) {
            redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Config cached: key={}", configKey);
        }
        return config.getConfigValue();
    }

    private void evictCache(String configKey) {
        String cacheKey = CONFIG_CACHE_PREFIX + configKey;
        redisTemplate.delete(cacheKey);
        log.debug("Config cache evicted: key={}", configKey);
    }

    private ConfigVO toVO(SysConfig config) {
        ConfigVO vo = new ConfigVO();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }
}
