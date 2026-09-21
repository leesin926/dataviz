package com.dataviz.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型数据集实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_dataset")
public class ModelDataset extends TenantEntity {

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * SQL查询语句
     */
    private String sqlQuery;

    /**
     * 维度配置 (JSON数组)
     */
    private String dimensions;

    /**
     * 指标配置 (JSON数组)
     */
    private String metrics;

    /**
     * 过滤条件 (JSON数组)
     */
    private String filters;
}
