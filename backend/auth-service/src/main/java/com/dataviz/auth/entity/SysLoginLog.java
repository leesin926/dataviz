package com.dataviz.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System login log entity.
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** User ID (null for failed login where user not found) */
    private Long userId;

    /** Login username */
    private String username;

    /** Login type: 1-password 2-sms 3-sso */
    private Integer loginType;

    /** Whether login was successful (stored as tinyint 0/1) */
    private Boolean status;

    /** Client IP address (column: ip) */
    private String ip;

    /** User agent string */
    private String userAgent;

    /** Message or error description */
    private String message;

    /** Login timestamp */
    private LocalDateTime loginTime;
}
