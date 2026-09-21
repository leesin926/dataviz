package com.dataviz.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 指标VO
 */
@Data
public class MetricVO {

    private Long id;
    private String name;
    private String displayName;
    private String expression;
    private String aggregationType;
    private Long datasourceId;
    private String tableName;
    private String description;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
