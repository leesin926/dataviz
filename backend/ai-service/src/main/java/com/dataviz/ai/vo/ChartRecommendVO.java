package com.dataviz.ai.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartRecommendVO {

    private String chartType;

    private String chartName;

    private Double score;

    private String reason;

    private List<String> suggestedMappings;
}
