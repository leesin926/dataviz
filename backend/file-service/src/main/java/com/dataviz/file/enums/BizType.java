package com.dataviz.file.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务类型枚举
 */
@Getter
@AllArgsConstructor
public enum BizType {

    AVATAR("avatar", "头像"),
    DATASET("dataset", "数据集"),
    DASHBOARD("dashboard", "仪表盘"),
    SCREEN("screen", "大屏"),
    REPORT("report", "报告"),
    ATTACHMENT("attachment", "附件");

    private final String code;
    private final String desc;
}
