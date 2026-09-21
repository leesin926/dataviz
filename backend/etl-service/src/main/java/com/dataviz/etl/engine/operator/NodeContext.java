package com.dataviz.etl.engine.operator;

import lombok.Builder;
import lombok.Data;

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
