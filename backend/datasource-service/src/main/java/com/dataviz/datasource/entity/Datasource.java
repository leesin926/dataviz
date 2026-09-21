package com.dataviz.datasource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("datasource")
public class Datasource extends TenantEntity {

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源类型 (MYSQL/POSTGRESQL/ORACLE/SQLSERVER/CLICKHOUSE/ELASTICSEARCH/MONGODB/API/CSV/EXCEL)
     */
    private String type;

    /**
     * 连接配置 (JSON格式, 包含host/port/database/username/password等)
     */
    private String config;

    /**
     * 状态 (1=启用, 0=禁用)
     */
    private Integer status;

    /**
     * 描述
     */
    private String description;
}
