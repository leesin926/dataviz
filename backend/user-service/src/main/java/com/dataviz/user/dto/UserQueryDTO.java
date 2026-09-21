package com.dataviz.user.dto;

import lombok.Data;

@Data
public class UserQueryDTO {
    private Long tenantId;
    private String username;
    private String nickname;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
