package com.dataviz.alert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知组 ↔ 联系人。
 * <p>
 * <b>没有 {@code deleted} 列，是有意的</b>：成员关系保存时整批替换，留着逻辑删除只会让
 * "这组现在有几个人"这个问题多一个回答不了的状态。全局 {@code logic-delete-field: deleted}
 * 只对<b>带这个字段</b>的实体生效，所以这里 {@code delete()} 走的是真删 —— 别误以为它像联系人那样可复原。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_notify_group_member")
public class AlertNotifyGroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long contactId;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
