package com.dataviz.analysis.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报告VO
 */
@Data
public class ReportVO {

    private Long id;
    private String name;
    private String description;
    private Long datasourceId;
    private Long datasetId;
    private String config;
    private Integer isPublished;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
