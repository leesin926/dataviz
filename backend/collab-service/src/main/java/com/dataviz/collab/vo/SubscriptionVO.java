package com.dataviz.collab.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionVO {

    private Long id;

    private Long tenantId;

    private Long userId;

    private String targetType;

    private Long targetId;

    private String notifyTypes;

    private LocalDateTime createTime;
}
