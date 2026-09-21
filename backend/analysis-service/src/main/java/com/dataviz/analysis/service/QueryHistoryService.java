package com.dataviz.analysis.service;

import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.common.core.result.PageResult;

public interface QueryHistoryService {
    Long saveQuery(Long userId, Long tenantId, AnalysisQueryDTO queryDTO, long duration);
    PageResult<?> getHistory(Long userId, int page, int size);
}
