package com.dataviz.analysis.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 查询结果VO
 */
@Data
public class QueryResultVO {

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 数据行
     */
    private List<Map<String, Object>> rows;

    /**
     * 返回行数
     */
    private Integer rowCount;

    /**
     * 执行耗时(毫秒)
     */
    private Long executionTime;

    /**
     * 执行的SQL
     */
    private String sql;
}
