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

    /**
     * 这条规则触发后发给哪几个通知组（收件人）。
     * <p>
     * 放在规则侧而不是渠道配置侧：渠道那边一个类型只会取<b>启用中的 id 最小那条</b>，
     * 所以收件人写在渠道 config 里就等于"所有用 EMAIL 的规则发同一批人"，改不动也分不开。
     */
    private List<Long> notifyGroupIds;
}
