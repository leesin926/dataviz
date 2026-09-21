package com.dataviz.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型指标实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_metric")
public class ModelMetric extends TenantEntity {

    /**
     * 指标名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 表达式
     */
    private String expression;

    /**
     * 聚合类型 (SUM/AVG/COUNT/MAX/MIN/CUSTOM)
     */
    private String aggregationType;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 描述
     */
    private String description;
}
