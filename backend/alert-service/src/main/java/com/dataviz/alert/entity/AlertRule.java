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
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * THRESHOLD / DERIVATIVE / COMPOSITE
     */
    private String type;

    private Long datasourceId;

    private String metricExpression;

    /**
     * GT / LT / EQ / GTE / LTE
     */
    @TableField("`condition`")
    private String condition;

    private java.math.BigDecimal threshold;

    /**
     * Duration in seconds
     */
    private Integer duration;

    /**
     * INFO / WARNING / CRITICAL
     */
    private String severity;

    /**
     * JSON array of channels: EMAIL / SMS / WEBHOOK / DINGTALK
     */
    private String notifyChannels;

    private Boolean enabled;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
