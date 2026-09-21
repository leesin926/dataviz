package com.dataviz.analysis.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * Analysis query DTO representing a visual query configuration.
 */
@Data
public class AnalysisQueryDTO {

    /** Dataset to query against */
    @NotNull(message = "Dataset ID is required")
    private Long datasetId;

    /** Dimension fields for grouping */
    private List<DimensionConfig> dimensions;

    /** Metric fields for aggregation */
    private List<MetricConfig> metrics;

    /** Filter conditions */
    private List<FilterConfig> filters;

    /** Metric-level filter conditions (HAVING) */
    private List<MetricFilterConfig> metricFilters;

    /** Sort order */
    private List<OrderConfig> orders;

    /** Maximum number of rows to return */
    private Integer limit;

    @Data
    public static class DimensionConfig {
        private String field;
        private String dateFormat;
    }

    @Data
    public static class MetricConfig {
        private String field;
        private String aggFunction;
        private String alias;
    }

    @Data
    public static class FilterConfig {
        private String field;
        private String operator;
        private Object value;
    }

    @Data
    public static class MetricFilterConfig {
        private String field;
        private String aggFunction;
        private String operator;
        private Object value;
    }

    @Data
    public static class OrderConfig {
        private String field;
        private String direction;
    }
}
