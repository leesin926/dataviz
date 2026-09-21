package com.dataviz.datasource.vo;

import lombok.Data;

/**
 * 表信息VO
 */
@Data
public class TableInfoVO {

    private String tableName;
    private String tableComment;
    private String tableType;
}
