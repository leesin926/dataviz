package com.dataviz.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dataviz.common.core.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行列数据权限规则（API-21 的规则来源）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_perm_rule")
public class DataPermRule extends TenantEntity {

    /** 规则名 */
    private String name;

    /** 作用数据集；null = 本租户全部数据集 */
    private Long datasetId;

    /** 命中方式：USER / ROLE */
    private String scopeType;

    /** USER 存用户ID，ROLE 存角色码 */
    private String scopeValue;

    /** 行条件 JSON：[{field,operator,value}]，规则内 AND */
    private String conditionsJson;

    /** 列权限：逗号分隔的物理列名 */
    private String restrictedColumns;

    /** 0=停用 */
    private Integer enabled;

    private String remark;
}
