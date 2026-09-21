package com.dataviz.user.vo;

import lombok.Data;
import java.util.List;

@Data
public class DeptTreeVO {
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String deptName;
    private Integer sortOrder;
    private String leader;
    private String phone;
    private Integer status;
    private List<DeptTreeVO> children;
}
