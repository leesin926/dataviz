package com.dataviz.datasource.vo;

import lombok.Data;

/**
 * 列信息VO
 */
@Data
public class ColumnInfoVO {

    private String columnName;
    private String dataType;
    private Integer columnSize;
    private Boolean nullable;
    private String defaultValue;
    private String comment;
    private Boolean primaryKey;
}
