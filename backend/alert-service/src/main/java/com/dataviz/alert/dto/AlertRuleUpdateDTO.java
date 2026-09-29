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

    /**
     * 通知组。null 表示"这次不改挂载关系"，空数组表示"一个都不发了" ——
     * 与 {@code notifyChannels} 的 null 语义保持一致，否则只想改阈值会把收件人一起冲掉。
     */
    private List<Long> notifyGroupIds;
}
