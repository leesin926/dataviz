package com.dataviz.user.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionTreeVO {
    private Long id;
    private Long parentId;
    private String permissionCode;
    private String permissionName;
    private Integer type;
    private String path;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private List<PermissionTreeVO> children;
}
