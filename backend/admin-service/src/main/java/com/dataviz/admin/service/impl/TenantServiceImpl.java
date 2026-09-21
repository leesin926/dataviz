package com.dataviz.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.admin.dto.TenantCreateDTO;
import com.dataviz.admin.dto.TenantUpdateDTO;
import com.dataviz.admin.entity.SysTenant;
import com.dataviz.admin.mapper.SysTenantMapper;
import com.dataviz.admin.service.TenantService;
import com.dataviz.admin.vo.TenantVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final SysTenantMapper tenantMapper;

    @Override
    @Transactional
    public Long create(TenantCreateDTO dto) {
        SysTenant tenant = new SysTenant();
        BeanUtils.copyProperties(dto, tenant);
        tenant.setStatus("ACTIVE");
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(tenant);
        log.info("Created tenant: id={}, name={}, code={}", tenant.getId(), tenant.getName(), tenant.getCode());
        return tenant.getId();
    }

    @Override
    @Transactional
    public void update(TenantUpdateDTO dto) {
        SysTenant tenant = tenantMapper.selectById(dto.getId());
        if (tenant == null) {
            throw new BizException("Tenant not found: " + dto.getId());
        }
        BeanUtils.copyProperties(dto, tenant);
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        log.info("Updated tenant: id={}", tenant.getId());
    }

    @Override
    public TenantVO getById(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("Tenant not found: " + id);
        }
        return toVO(tenant);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        tenantMapper.deleteById(id);
        log.info("Deleted tenant: id={}", id);
    }

    @Override
    public PageResult<TenantVO> page(PageQuery pageQuery, String keyword, String status) {
        Page<SysTenant> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysTenant::getName, keyword).or().like(SysTenant::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysTenant::getStatus, status);
        }
        wrapper.orderByDesc(SysTenant::getCreateTime);
        Page<SysTenant> result = tenantMapper.selectPage(page, wrapper);
        List<TenantVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    @Transactional
    public void enable(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("Tenant not found: " + id);
        }
        tenant.setStatus("ACTIVE");
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        log.info("Enabled tenant: id={}", id);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("Tenant not found: " + id);
        }
        tenant.setStatus("DISABLED");
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        log.info("Disabled tenant: id={}", id);
    }

    @Override
    public boolean checkExpire(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("Tenant not found: " + id);
        }
        if (tenant.getExpireTime() == null) {
            return false; // No expiration set
        }
        return tenant.getExpireTime().isBefore(LocalDateTime.now());
    }

    private TenantVO toVO(SysTenant tenant) {
        TenantVO vo = new TenantVO();
        BeanUtils.copyProperties(tenant, vo);
        return vo;
    }
}
