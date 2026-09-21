package com.dataviz.alert.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventVO {

    private Long id;

    private Long ruleId;

    private String ruleName;

    private BigDecimal triggerValue;

    private String severity;

    /**
     * PENDING / ACKNOWLEDGED / RESOLVED
     */
    private String status;

    private String message;

    private LocalDateTime notifiedAt;

    private LocalDateTime resolvedAt;

    private Long tenantId;

    private LocalDateTime createTime;
}
