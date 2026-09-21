package com.dataviz.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AlertRuleUpdateDTO {

    private Long id;

    private String name;

    private String description;

    private String type;

    private Long datasourceId;

    private String metricExpression;

    private String condition;

    private BigDecimal threshold;

    private Integer duration;

    private String severity;

    private List<String> notifyChannels;
}
