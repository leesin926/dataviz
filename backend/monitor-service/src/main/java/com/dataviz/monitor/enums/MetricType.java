package com.dataviz.monitor.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 指标类型枚举
 */
@Getter
@AllArgsConstructor
public enum MetricType {

    GAUGE("GAUGE", "瞬时值"),
    COUNTER("COUNTER", "计数器"),
    HISTOGRAM("HISTOGRAM", "直方图/分布");

    private final String code;
    private final String desc;
}
