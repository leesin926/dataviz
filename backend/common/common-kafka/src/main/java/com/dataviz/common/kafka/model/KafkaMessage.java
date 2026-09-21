package com.dataviz.common.kafka.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic Kafka message wrapper
 */
public class KafkaMessage<T> implements Serializable {

    private String id;
    private String topic;
    private String key;
    private T payload;
    private Instant timestamp;
    private Map<String, String> headers;

    public KafkaMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.headers = new HashMap<>();
    }

    public KafkaMessage(String topic, String key, T payload) {
        this();
        this.topic = topic;
        this.key = key;
        this.payload = payload;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public KafkaMessage<T> addHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public static class Builder<T> {
        private final KafkaMessage<T> message = new KafkaMessage<>();

        public Builder<T> topic(String topic) { message.setTopic(topic); return this; }
        public Builder<T> key(String key) { message.setKey(key); return this; }
        public Builder<T> payload(T payload) { message.setPayload(payload); return this; }
        public Builder<T> header(String name, String value) { message.addHeader(name, value); return this; }
        public Builder<T> headers(Map<String, String> headers) { message.setHeaders(headers); return this; }

        public KafkaMessage<T> build() { return message; }
    }
}
