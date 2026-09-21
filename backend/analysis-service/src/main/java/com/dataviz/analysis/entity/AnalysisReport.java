package com.dataviz.analysis.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分析报告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_report")
public class AnalysisReport extends TenantEntity {

    /**
     * 报告名称
     */
    private String name;

    /**
     * 报告描述
     */
    private String description;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 数据集ID
     */
    private Long datasetId;

    /**
     * 图表配置 (JSON格式)
     */
    private String config;

    /**
     * 是否已发布 (0=未发布, 1=已发布)
     */
    private Integer isPublished;
}
