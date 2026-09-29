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
 * 告警规则 ↔ 通知组（规则"这次要发给哪几组"）。
 * <p>
 * 做成关系表而不是在 {@code alert_rule} 上加一个 JSON 列，判据是<b>删除守卫要反查</b>：
 * "这个组被哪些规则在用"必须问得准。JSON + {@code LIKE %900%} 会把 900 和 9001 一起命中，
 * 于是守卫会在不该拦的时候拦、或者放过真正在用的那条 —— 这类"看着能用其实判据是错的"形状最难发现。
 * <p>
 * 同样没有 {@code deleted} 列：随规则保存整批替换，规则物理删除时一起清掉。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("alert_rule_notify_group")
public class AlertRuleNotifyGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private Long groupId;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
