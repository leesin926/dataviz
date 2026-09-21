package com.dataviz.user.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeptCreateDTO {
    private Long parentId;
    @NotBlank(message = "Department name is required")
    private String deptName;
    private Integer sortOrder;
    private String leader;
    private String phone;
}
