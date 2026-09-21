package com.dataviz.model.entity;

/**
 * 聚合类型枚举
 */
public enum AggregationType {

    SUM("求和"),
    AVG("平均值"),
    COUNT("计数"),
    MAX("最大值"),
    MIN("最小值"),
    CUSTOM("自定义表达式");

    private final String description;

    AggregationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
