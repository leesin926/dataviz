package com.dataviz.schedule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务执行日志状态枚举
 */
@Getter
@AllArgsConstructor
public enum JobLogStatus {

    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;
}
