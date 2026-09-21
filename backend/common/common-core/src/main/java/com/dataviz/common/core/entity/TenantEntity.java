package com.dataviz.common.core.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体类 - 支持多租户
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity {

    /**
     * 租户ID
     */
    private String tenantId;
}
