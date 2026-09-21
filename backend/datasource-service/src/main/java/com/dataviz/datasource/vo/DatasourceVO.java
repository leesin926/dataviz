package com.dataviz.datasource.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源详情VO
 */
@Data
public class DatasourceVO {

    private Long id;
    private String name;
    private String type;
    private String config;
    private Integer status;
    private String description;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
