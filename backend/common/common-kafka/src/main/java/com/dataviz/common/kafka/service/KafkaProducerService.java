package com.dataviz.common.kafka.service;

import com.dataviz.common.kafka.model.KafkaMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.function.BiConsumer;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public ListenableFuture<SendResult<String, Object>> send(String topic, String key, Object value) {
        String serialized = serialize(value);
        log.debug("Sending message to topic={}, key={}", topic, key);
        return kafkaTemplate.send(topic, key, serialized);
    }

    public ListenableFuture<SendResult<String, Object>> sendAsync(String topic, String key, Object value) {
        String serialized = serialize(value);
        ListenableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, serialized);
        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onSuccess(SendResult<String, Object> result) {
                log.debug("Async send succeeded topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Async send failed topic={}, key={}", topic, key, ex);
            }
        });
        return future;
    }

    public void sendWithCallback(String topic, String key, Object value,
                                  BiConsumer<SendResult<String, Object>, Throwable> callback) {
        String serialized = serialize(value);
        ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, serialized);
        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onSuccess(SendResult<String, Object> result) {
                callback.accept(result, null);
            }

            @Override
            public void onFailure(Throwable ex) {
                callback.accept(null, ex);
            }
        });
    }

    public <T> ListenableFuture<SendResult<String, Object>> sendMessage(KafkaMessage<T> message) {
        return sendAsync(message.getTopic(), message.getKey(), message);
    }

    private String serialize(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Serialization error", e);
            throw new IllegalArgumentException("Cannot serialize value to JSON", e);
        }
    }
}
