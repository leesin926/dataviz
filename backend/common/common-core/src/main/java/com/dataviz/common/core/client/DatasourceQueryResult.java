package com.dataviz.common.core.client;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * datasource-service 内部查询端点的返回结构（columns/rows/rowCount/executionTime/sql）。
 */
@Data
public class DatasourceQueryResult {

    private List<String> columns = Collections.emptyList();

    private List<Map<String, Object>> rows = Collections.emptyList();

    private int rowCount;

    private Long executionTime;

    private String sql;
}
