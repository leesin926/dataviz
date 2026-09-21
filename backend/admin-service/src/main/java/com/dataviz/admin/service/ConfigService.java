package com.dataviz.admin.service;

import com.dataviz.admin.dto.ConfigCreateDTO;
import com.dataviz.admin.vo.ConfigVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

/**
 * Config CRUD, getByKey (with Redis cache)
 */
public interface ConfigService {

    Long create(ConfigCreateDTO dto);

    void update(ConfigCreateDTO dto);

    ConfigVO getById(Long id);

    void delete(Long id);

    PageResult<ConfigVO> page(PageQuery pageQuery, String keyword, String configType);

    /**
     * Get config value by key with Redis cache
     */
    String getByKey(String configKey);
}
