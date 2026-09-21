package com.dataviz.user.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Long deptId;
    private String deptName;
    private LocalDateTime loginTime;
    private LocalDateTime createTime;
    private List<Long> roleIds;
}
