package com.dataviz.schedule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum JobStatus {

    RUNNING("RUNNING", "运行中"),
    PAUSED("PAUSED", "已暂停"),
    ERROR("ERROR", "异常");

    private final String code;
    private final String desc;
}
