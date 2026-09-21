package com.dataviz.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AlertRuleCreateDTO {

    private String name;

    private String description;

    /**
     * THRESHOLD / DERIVATIVE / COMPOSITE
     */
    private String type;

    private Long datasourceId;

    private String metricExpression;

    /**
     * GT / LT / EQ / GTE / LTE
     */
    private String condition;

    private BigDecimal threshold;

    /**
     * Duration in seconds
     */
    private Integer duration;

    /**
     * INFO / WARNING / CRITICAL
     */
    private String severity;

    /**
     * List of channels: EMAIL / SMS / WEBHOOK / DINGTALK
     */
    private List<String> notifyChannels;
}
