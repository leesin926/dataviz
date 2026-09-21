package com.dataviz.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System permission entity.
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Parent permission ID for tree structure */
    private Long parentId;

    /** Permission code (e.g., "user:create", "dashboard:view") */
    private String permissionCode;

    /** Permission display name */
    private String permissionName;

    /** Permission type: 1=menu, 2=button, 3=api */
    private Integer type;

    /** Associated path or URL pattern */
    private String path;

    /** Icon for menu items */
    private String icon;

    /** Sort order */
    private Integer sortOrder;

    /** Status: 1=active, 0=disabled */
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("deleted")
    private Integer deleted;
}
