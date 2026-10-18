package com.dataviz.etl.engine.operator;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Execution context passed to each operator during DAG execution.
 * <p>
 * Contains the node configuration, input data from upstream nodes,
 * and shared state accessible across the DAG execution.
 * </p>
 */
@Data
@Builder
public class NodeContext {

    /** Node ID within the DAG */
    private String nodeId;

    /** Operator type for this node */
    private String operatorType;

    /** Node-specific configuration from the DAG definition */
    private Map<String, Object> config;

    /** Input data from upstream nodes (list of row maps) */
    private List<Map<String, Object>> inputData;

    /** Shared execution state across the DAG */
    private Map<String, Object> sharedState;

    /** Instance ID for tracking and cancellation */
    private Long instanceId;

    /** Flag to indicate if execution should stop */
    @Builder.Default
    private volatile boolean cancelled = false;

    /**
     * Get a config value by key.
     */
    public Object getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }

    /**
     * Get a config value as String.
     */
    public String getConfigString(String key) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a config value as Integer.
     */
    public Integer getConfigInt(String key) {
        Object value = getConfigValue(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    /**
     * Get a config value as Long. Accepts a JSON number or its string form.
     */
    public Long getConfigLong(String key) {
        Object value = getConfigValue(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 不是合法 ID: " + text);
        }
    }

    /**
     * Get a config value as a list of trimmed strings.
     * <p>
     * 设计器把列表存成 JSON 数组，演示数据和手改的 DAG 里是同串 —— 两种都接。这里不收
     * "只有一种形状"的假设：另一种一来，症状是整个任务跑不起来，而报错只会说"配置缺字段"。
     */
    public List<String> getConfigList(String key) {
        Object value = getConfigValue(key);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                addIfPresent(out, item);
            }
            return out;
        }
        for (String part : value.toString().split(",")) {
            addIfPresent(out, part);
        }
        return out;
    }

    private static void addIfPresent(List<String> target, Object value) {
        if (value == null) {
            return;
        }
        String trimmed = value.toString().trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    /**
     * Put a value into shared state.
     */
    public void putSharedState(String key, Object value) {
        if (sharedState == null) {
            sharedState = new ConcurrentHashMap<>();
        }
        sharedState.put(key, value);
    }

    /**
     * Get a value from shared state.
     */
    public Object getSharedState(String key) {
        return sharedState != null ? sharedState.get(key) : null;
    }
}
