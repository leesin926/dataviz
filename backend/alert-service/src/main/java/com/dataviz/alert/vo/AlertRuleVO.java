package com.dataviz.alert.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleVO {

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

    private Boolean enabled;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
