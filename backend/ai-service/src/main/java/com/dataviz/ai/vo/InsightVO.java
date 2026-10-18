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

    /**
     * 这段洞察是不是大模型写的。
     * <p>洞察是落库的，所以这个标记随 config 一起持久化（{@code config.llmUsed}），
     * 列表与详情都能还原，避免"当时是占位、后来配了模型"被误读成真结论。</p>
     */
    private Boolean llmUsed;

    private String llmNote;
}
