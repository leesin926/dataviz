package com.dataviz.etl.entity;

/**
 * ETL任务状态枚举
 */
public enum EtlTaskStatus {

    STOPPED("已停止"),
    RUNNING("运行中"),
    PAUSED("已暂停"),
    ERROR("错误"),
    COMPLETED("已完成");

    private final String description;

    EtlTaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
