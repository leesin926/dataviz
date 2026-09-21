package com.dataviz.analysis.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询历史实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("query_history")
public class QueryHistory extends TenantEntity {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 执行的SQL
     */
    @TableField("`sql`")
    private String sql;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 执行耗时(毫秒)
     */
    private Long executionTime;

    /**
     * 返回行数
     */
    private Integer rowCount;
}
