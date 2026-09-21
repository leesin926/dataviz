package com.dataviz.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据集VO
 */
@Data
public class DatasetVO {

    private Long id;
    private String name;
    private Long datasourceId;
    private String tableName;
    private String sqlQuery;
    private String dimensions;
    private String metrics;
    private String filters;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
