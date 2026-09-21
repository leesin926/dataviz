package com.dataviz.admin.service;

import com.dataviz.admin.dto.TenantCreateDTO;
import com.dataviz.admin.dto.TenantUpdateDTO;
import com.dataviz.admin.vo.TenantVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

/**
 * Tenant CRUD, enable/disable, check expiration
 */
public interface TenantService {

    Long create(TenantCreateDTO dto);

    void update(TenantUpdateDTO dto);

    TenantVO getById(Long id);

    void delete(Long id);

    PageResult<TenantVO> page(PageQuery pageQuery, String keyword, String status);

    void enable(Long id);

    void disable(Long id);

    /**
     * Check if the tenant has expired
     */
    boolean checkExpire(Long id);
}
