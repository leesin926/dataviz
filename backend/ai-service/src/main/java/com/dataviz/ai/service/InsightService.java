package com.dataviz.ai.service;

import com.dataviz.ai.dto.InsightGenerateDTO;
import com.dataviz.ai.vo.InsightVO;

import java.util.List;

/**
 * Auto-generate data insights (trend, outlier, correlation analysis)
 */
public interface InsightService {

    /**
     * Generate a data insight
     */
    InsightVO generateInsight(InsightGenerateDTO dto);

    /**
     * Get insights for a dataset
     */
    List<InsightVO> getInsights(Long datasourceId, Long datasetId, String insightType);

    /**
     * Get insight detail
     */
    InsightVO getInsightDetail(Long id);
}
