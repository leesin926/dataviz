package com.dataviz.user.vo;

import lombok.Data;

@Data
public class RoleVO {
    private Long id;
    private Long tenantId;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortOrder;
    private Integer status;
}
