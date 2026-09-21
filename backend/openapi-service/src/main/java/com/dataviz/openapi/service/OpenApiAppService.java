package com.dataviz.openapi.service;

import com.dataviz.openapi.dto.AppCreateDTO;
import com.dataviz.openapi.vo.AppVO;

import java.util.List;

public interface OpenApiAppService {

    Long create(AppCreateDTO dto);

    void delete(Long id);

    AppVO getById(Long id);

    List<AppVO> list(Long tenantId);

    AppVO regenerateSecret(Long id);
}
