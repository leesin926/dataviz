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

    /**
     * 结果是否被行数上限截断（true 时 rows 不是全量，别拿去当"总共就这些"）
     */
    private boolean truncated;
}
