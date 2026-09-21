package com.dataviz.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System user entity.
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Tenant ID for multi-tenant isolation */
    private Long tenantId;

    /** Login username */
    private String username;

    /** Encrypted password */
    private String password;

    /** Display name */
    private String nickname;

    /** Email address */
    private String email;

    /** Phone number */
    private String phone;

    /** Avatar URL */
    private String avatar;

    /** Account status: 1=active, 0=disabled */
    private Integer status;

    /** Last login IP */
    private String loginIp;

    /** Last login time */
    private LocalDateTime loginTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("create_by")
    private String createBy;

    @TableField("update_by")
    private String updateBy;

    @TableField("deleted")
    private Integer deleted;
}
