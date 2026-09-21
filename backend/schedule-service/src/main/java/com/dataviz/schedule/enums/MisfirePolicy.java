package com.dataviz.schedule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Misfire处理策略枚举
 */
@Getter
@AllArgsConstructor
public enum MisfirePolicy {

    IGNORE("IGNORE", "忽略 Misfire"),
    FIRE_ONCE("FIRE_ONCE", "触发一次执行"),
    EXECUTE_ALL("EXECUTE_ALL", "执行所有错过的任务");

    private final String code;
    private final String desc;
}
