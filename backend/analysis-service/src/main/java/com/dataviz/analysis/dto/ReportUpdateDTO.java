package com.dataviz.analysis.dto;

import lombok.Data;

/**
 * 报告更新DTO
 */
@Data
public class ReportUpdateDTO {

    private String name;

    private String description;

    private Long datasourceId;

    private Long datasetId;

    private String config;
}
