package com.dataviz.common.kafka.publish;

import com.dataviz.common.kafka.event.DomainEvent;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.Map;

/**
 * 事件发布器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发布事件（同步）
     */
    public void publish(DomainEvent event) {
        try {
            enrichEvent(event);
            ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(event.getTopic(), event);
            future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
                @Override
                public void onSuccess(SendResult<String, Object> result) {
                    log.debug("事件发送成功: topic={}, eventId={}", event.getTopic(), event.getEventId());
                }

                @Override
                public void onFailure(Throwable ex) {
                    log.error("事件发送失败: topic={}, eventId={}", event.getTopic(), event.getEventId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("发布事件异常: {}", event.getEventId(), e);
        }
    }

    /**
     * 发布事件（简化版）
     */
    public void publish(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        DomainEvent event = DomainEvent.builder()
                .topic(topic)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .build();
        publish(event);
    }

    /**
     * 异步发布事件
     */
    @Async
    public void publishAsync(DomainEvent event) {
        publish(event);
    }

    /**
     * 填充事件上下文信息
     */
    private void enrichEvent(DomainEvent event) {
        if (event.getSource() == null) {
            event.setSource(System.getProperty("spring.application.name", "unknown"));
        }
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        if (loginUser != null) {
            if (event.getOperatorId() == null) {
                event.setOperatorId(loginUser.getUserId());
            }
            if (event.getOperatorName() == null) {
                event.setOperatorName(loginUser.getUsername());
            }
            if (event.getTenantId() == null) {
                event.setTenantId(loginUser.getTenantId());
            }
        }
    }
}
