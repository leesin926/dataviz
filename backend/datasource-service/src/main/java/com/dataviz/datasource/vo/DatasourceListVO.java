package com.dataviz.datasource.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源列表VO (精简)
 */
@Data
public class DatasourceListVO {

    private Long id;
    private String name;
    private String type;
    private Integer status;
    private String description;
    private LocalDateTime createTime;
}
