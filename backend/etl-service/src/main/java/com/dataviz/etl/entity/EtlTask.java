package com.dataviz.etl.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * ETL任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("etl_task")
public class EtlTask extends TenantEntity {

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 源数据源ID
     */
    private Long sourceDatasourceId;

    /**
     * 目标数据源ID
     */
    private Long targetDatasourceId;

    /**
     * 源表名
     */
    private String sourceTable;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * 转换配置 (JSON格式)
     */
    private String transformConfig;

    /**
     * 调度Cron表达式
     */
    private String scheduleCron;

    /**
     * 任务状态 (STOPPED/RUNNING/PAUSED/ERROR/COMPLETED)
     */
    private String status;

    /**
     * 上次运行时间
     */
    private LocalDateTime lastRunTime;

    /**
     * 上次运行状态
     */
    private String lastRunStatus;
}
