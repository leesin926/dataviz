package com.dataviz.model.vo;

import lombok.Data;

/**
 * 数据集执行元数据：够调用方拼出 FROM 并定位数据源，不含任何口令。
 */
@Data
public class DatasetMetaVO {

    private Long datasetId;

    /** 归属租户，调用方用它做跨租户兜底校验 */
    private String tenantId;

    private String name;

    /** 数据源 ID（datasource-service 的主键），执行 SQL 必须按它路由 */
    private Long datasourceId;

    /** 物理表名，与 sqlQuery 二选一 */
    private String tableName;

    /** 自定义 SQL，非空时作为子查询来源 */
    private String sqlQuery;

    /** 维度配置 JSON 数组（字段名列表用于校验查询列） */
    private String dimensions;

    /** 指标配置 JSON 数组 */
    private String metrics;
}
