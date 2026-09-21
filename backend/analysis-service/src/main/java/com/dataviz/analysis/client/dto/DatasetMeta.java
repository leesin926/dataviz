package com.dataviz.analysis.client.dto;

import lombok.Data;

/**
 * model-service 数据集元数据的本地镜像（服务间只走 HTTP，不依赖对方 jar，故按 JSON 字段对齐）。
 * <p>字段增减必须与 model-service 的 DatasetMetaVO 同步，analysis 侧只读不用。</p>
 */
@Data
public class DatasetMeta {

    private Long datasetId;

    /** 归属租户；调用方带租户上下文时用于兜底校验 */
    private String tenantId;

    private String name;

    /** 数据源 ID，执行 SQL 按它路由 */
    private Long datasourceId;

    /** 物理表名，与 sqlQuery 二选一 */
    private String tableName;

    /** 自定义 SQL，非空时作为子查询来源 */
    private String sqlQuery;

    private String dimensions;

    private String metrics;
}
