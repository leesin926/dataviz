package com.dataviz.ai.service.impl;

import com.dataviz.ai.service.ChartRecommendService;
import com.dataviz.ai.vo.ChartRecommendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChartRecommendServiceImpl implements ChartRecommendService {

    @Override
    public List<ChartRecommendVO> recommend(List<String> columns, List<Map<String, Object>> data) {
        log.info("Recommending chart for {} columns and {} rows", columns.size(), data.size());
        List<ChartRecommendVO> recommendations = new ArrayList<>();

        int columnCount = columns.size();
        boolean hasTimeColumn = columns.stream().anyMatch(c -> 
                c.toLowerCase().contains("date") || c.toLowerCase().contains("time"));
        boolean hasNumericColumns = columns.size() > 1;

        // Rule-based chart recommendation
        if (columnCount == 1) {
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("pie")
                    .chartName("Pie Chart")
                    .score(0.9)
                    .reason("Single categorical column is best displayed as a pie chart")
                    .suggestedMappings(Arrays.asList("category=" + columns.get(0)))
                    .build());
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("bar")
                    .chartName("Bar Chart")
                    .score(0.7)
                    .reason("Categorical data can also be shown as bar chart")
                    .suggestedMappings(Arrays.asList("x=" + columns.get(0)))
                    .build());
        } else if (hasTimeColumn && hasNumericColumns) {
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("line")
                    .chartName("Line Chart")
                    .score(0.95)
                    .reason("Time series data is best visualized with a line chart")
                    .suggestedMappings(Arrays.asList("x=time", "y=numeric_column"))
                    .build());
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("area")
                    .chartName("Area Chart")
                    .score(0.8)
                    .reason("Area chart shows cumulative trends over time")
                    .suggestedMappings(Arrays.asList("x=time", "y=numeric_column"))
                    .build());
        } else if (columnCount == 2 && hasNumericColumns) {
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("scatter")
                    .chartName("Scatter Plot")
                    .score(0.85)
                    .reason("Two numeric columns are ideal for scatter plot correlation")
                    .suggestedMappings(Arrays.asList("x=" + columns.get(0), "y=" + columns.get(1)))
                    .build());
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("bar")
                    .chartName("Bar Chart")
                    .score(0.75)
                    .reason("Bar chart for categorical vs numeric comparison")
                    .suggestedMappings(Arrays.asList("x=" + columns.get(0), "y=" + columns.get(1)))
                    .build());
        } else {
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("table")
                    .chartName("Data Table")
                    .score(0.7)
                    .reason("Multiple columns with mixed types are best shown in a table")
                    .suggestedMappings(columns.stream().map(c -> "col=" + c).collect(Collectors.toList()))
                    .build());
            recommendations.add(ChartRecommendVO.builder()
                    .chartType("bar")
                    .chartName("Grouped Bar Chart")
                    .score(0.65)
                    .reason("Grouped bar chart for multi-column comparison")
                    .suggestedMappings(Arrays.asList("x=" + columns.get(0), "y=" + columns.get(1)))
                    .build());
        }

        return recommendations;
    }
}
