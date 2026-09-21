package com.dataviz.analysis.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 报告数据VO (包含报告信息和查询结果)
 */
@Data
public class ReportDataVO {

    private ReportVO report;

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
}
