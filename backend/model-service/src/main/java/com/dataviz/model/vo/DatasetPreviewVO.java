package com.dataviz.model.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 数据集预览VO
 */
@Data
public class DatasetPreviewVO {

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 数据行
     */
    private List<Map<String, Object>> rows;

    /**
     * 行数
     */
    private Integer rowCount;

    /**
     * 执行的SQL
     */
    private String sql;
}
