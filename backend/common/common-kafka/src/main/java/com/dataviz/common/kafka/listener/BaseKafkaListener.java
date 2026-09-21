package com.dataviz.common.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Abstract base class for Kafka listeners using the template-method pattern.
 * Subclasses implement handleMessage(); retry and error handling are provided.
 */
public abstract class BaseKafkaListener {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final RetryTemplate retryTemplate;

    protected BaseKafkaListener() {
        this.retryTemplate = buildRetryTemplate(maxRetries(), retryIntervalMs());
    }

    /**
     * Subclasses implement this to handle the deserialized message payload.
     */
    protected abstract void handleMessage(String topic, String key, String value) throws Exception;

    /**
     * Hook invoked after successful processing. Override if needed.
     */
    protected void onSuccess(String topic, String key, String value) {
        log.debug("Message processed successfully topic={}, key={}", topic, key);
    }

    /**
     * Hook invoked after all retries are exhausted. Override for custom error logic.
     */
    protected void onError(String topic, String key, String value, Exception ex) {
        log.error("Message processing failed after {} retries topic={}, key={}", maxRetries(), topic, key, ex);
    }

    /**
     * Template method called from the concrete @KafkaListener method.
     */
    protected void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        String key = record.key();
        String value = record.value();
        log.debug("Received message topic={}, partition={}, offset={}, key={}",
                topic, record.partition(), record.offset(), key);

        try {
            retryTemplate.execute(context -> {
                handleMessage(topic, key, value);
                return null;
            });
            onSuccess(topic, key, value);
            ack.acknowledge();
        } catch (Exception ex) {
            onError(topic, key, value, ex);
            ack.acknowledge();
        }
    }

    /** Override to change max retry count (default 3). */
    protected int maxRetries() { return 3; }

    /** Override to change retry interval in ms (default 1000). */
    protected long retryIntervalMs() { return 1000L; }

    private RetryTemplate buildRetryTemplate(int maxAttempts, long intervalMs) {
        RetryTemplate template = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxAttempts);
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(intervalMs);
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOff);
        return template;
    }
}
