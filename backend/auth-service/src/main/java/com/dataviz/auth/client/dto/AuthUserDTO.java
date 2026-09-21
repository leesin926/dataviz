package com.dataviz.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * user-service 内部接口返回的认证视图（对应 AuthUserVO）。
 * 刻意不含 loginTime/loginIp 等认证用不到的字段，出现新字段时也直接忽略，避免对端加字段就把调用打断。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserDTO {
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private Long deptId;
    private String password;
    private Integer status;
}
