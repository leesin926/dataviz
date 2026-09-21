package com.dataviz.datasource.dto;

import lombok.Data;

/**
 * 数据源更新DTO
 */
@Data
public class DatasourceUpdateDTO {

    private String name;

    private String type;

    private String config;

    private Integer status;

    private String description;
}
