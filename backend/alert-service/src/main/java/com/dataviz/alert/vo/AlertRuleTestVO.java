package com.dataviz.alert.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 规则试跑结果：只取数 + 判定，<b>不产生事件、不发通知</b>。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleTestVO {

    /** 指标 SQL 当前取到的值；null 表示查询成功但无结果行 */
    private BigDecimal value;

    /** 该值是否命中告警条件 */
    private boolean triggered;

    /** 说明（例如"指标查询无结果"）；正常判定为 null */
    private String note;
}
