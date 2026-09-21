package com.dataviz.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class InsightRequestDTO {

    private Long datasetId;

    private List<String> columns;

    private List<Map<String, Object>> data;

    private String insightType;
}
