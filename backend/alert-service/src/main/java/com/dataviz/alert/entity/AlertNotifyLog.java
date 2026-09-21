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
@TableName("alert_notify_log")
public class AlertNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    /**
     * EMAIL / SMS / WEBHOOK / DINGTALK
     */
    private String channel;

    private String recipient;

    /**
     * SUCCESS / FAILED
     */
    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
