package com.dataviz.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型维度实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_dimension")
public class ModelDimension extends TenantEntity {

    /**
     * 维度名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 列名
     */
    private String columnName;

    /**
     * 描述
     */
    private String description;
}
