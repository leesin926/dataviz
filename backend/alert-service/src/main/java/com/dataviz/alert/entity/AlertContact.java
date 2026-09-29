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
 * 告警联系人：通知对象的最小单位。
 * <p>
 * 只有"可达地址"（email / mobile）而没有渠道概念 —— 同一个人既收邮件又被钉钉 @，
 * 配一遍就够，这是把收件人从 {@code notify_channel.config} 里挪出来的主要收益之一。
 * <p>
 * 停用而不是删除：删掉会让历史组的成员数凭空变化，值班时看不出"这组上个月还少一个人"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_contact")
public class AlertContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String email;

    private String mobile;

    private String remark;

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
