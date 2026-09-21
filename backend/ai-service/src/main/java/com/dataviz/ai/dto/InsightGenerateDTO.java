package com.dataviz.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class InsightGenerateDTO {

    private Long datasourceId;

    private Long datasetId;

    /**
     * TREND / OUTLIER / CORRELATION / DISTRIBUTION
     */
    private String insightType;

    /**
     * Optional configuration parameters for insight generation
     */
    private Map<String, Object> config;
}
