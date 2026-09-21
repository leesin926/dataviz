package com.dataviz.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 维度VO
 */
@Data
public class DimensionVO {

    private Long id;
    private String name;
    private String displayName;
    private String dataType;
    private Long datasourceId;
    private String tableName;
    private String columnName;
    private String description;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
