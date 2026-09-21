package com.dataviz.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 领域事件记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent implements Serializable {

    
    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件主题
     */
    private String topic;

    /**
     * 事件来源（服务名称）
     */
    private String source;

    /**
     * 聚合根ID
     */
    private String aggregateId;

    /**
     * 事件数据
     */
    private Map<String, Object> payload;

    /**
     * 事件发生时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人用户名
     */
    private String operatorName;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 事件版本号
     */
    @Builder.Default
    private String version = "1.0";
}
