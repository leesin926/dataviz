package com.dataviz.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知组：一撮联系人，规则引用的单位。
 * <p>
 * 规则挂组而不是挂人，是为了"换值班表只改一处"：一条规则勾十个人、十个组各勾十个人，
 * 改一次名单要点开十个地方；勾组则名单只有一份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_notify_group")
public class AlertNotifyGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Boolean enabled;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
