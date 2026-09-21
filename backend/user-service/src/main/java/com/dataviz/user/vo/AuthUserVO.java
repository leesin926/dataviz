package com.dataviz.user.vo;

import lombok.Data;

/**
 * 认证视图 —— 只给 auth-service 的内部接口使用，含密码哈希，禁止挂到任何对外端点上。
 * 字段按认证实际需要收敛：邮箱/手机/头像/上次登录时间一概不出网。
 */
@Data
public class AuthUserVO {
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private Long deptId;
    /** bcrypt 哈希，由 auth-service 做 matches 校验 */
    private String password;
    /** 0=禁用 1=正常；认证侧据此拦截登录 */
    private Integer status;
}
