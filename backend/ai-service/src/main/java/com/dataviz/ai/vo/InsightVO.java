package com.dataviz.ai.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightVO {

    private Long id;

    private Long datasourceId;

    private Long datasetId;

    /**
     * TREND / OUTLIER / CORRELATION / DISTRIBUTION
     */
    private String insightType;

    private String content;

    private Map<String, Object> config;

    private Long tenantId;

    private LocalDateTime createTime;
}
