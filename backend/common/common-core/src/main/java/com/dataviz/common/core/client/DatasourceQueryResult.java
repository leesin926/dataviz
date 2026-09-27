package com.dataviz.common.core.client;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * datasource-service 内部查询端点的返回结构（columns/rows/rowCount/executionTime/sql/truncated）。
 */
@Data
public class DatasourceQueryResult {

    private List<String> columns = Collections.emptyList();

    private List<Map<String, Object>> rows = Collections.emptyList();

    private int rowCount;

    private Long executionTime;

    private String sql;

    /** 是否还有数据被行数上限截掉了。调用方没法从 rowCount 推断：正好等于上限时既可能是截断也可能是不多不少。 */
    private boolean truncated;
}
