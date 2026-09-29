package com.dataviz.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notify_channel")
public class NotifyChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * EMAIL / SMS / WEBHOOK / DINGTALK / WECHAT / FEISHU（大写，与 AlertNotifier.channel() 一致）
     */
    private String type;

    private String config;

    private Boolean enabled;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
