package com.dataviz.monitor.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 告警状态枚举
 */
@Getter
@AllArgsConstructor
public enum AlertStatus {

    ACTIVE("ACTIVE", "活跃/未处理"),
    RESOLVED("RESOLVED", "已解决");

    private final String code;
    private final String desc;
}
