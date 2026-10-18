package com.dataviz.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台许可证（一张已激活的密钥 + 它的历史）。
 * <p>不带 tenant_id：许可证是整个部署的属性，不是某个租户的属性，所以这张表在
 * {@code MybatisPlusConfig.IGNORE_TENANT_TABLES} 里必须出现 —— 漏了的症状是登录态一查许可证就报
 * {@code Unknown column 'tenant_id'}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_license")
public class SysLicense {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 密钥指纹（sha256 前 6 字节），<b>不存原文</b>：这张表被拖库时不该顺带给出可用的签发材料 */
    private String keyFingerprint;

    private String edition;

    private String issuedTo;

    private LocalDate issuedAt;

    private LocalDate expiresAt;

    private Integer maxUsers;

    private Integer maxNodes;

    /** 1 = 当前生效，0 = 已被后续激活顶替 */
    private Integer status;

    private LocalDateTime activateTime;

    /** 激活人用户名，便于审计"这张钥匙是谁什么时候换的" */
    private String activatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
