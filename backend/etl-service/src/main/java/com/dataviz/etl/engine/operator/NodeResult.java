package com.dataviz.etl.engine.operator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of an operator node execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResult {

    /** Whether the execution was successful */
    private boolean success;

    /** Output data produced by this node */
    private List<Map<String, Object>> outputData;

    /** Error message if execution failed */
    private String errorMessage;

    /** Number of rows processed */
    private long rowCount;

    /** Execution duration in milliseconds */
    private long durationMs;

    /** Additional metadata */
    private Map<String, Object> metadata;

    /**
     * Create a successful result with output data.
     */
    public static NodeResult success(List<Map<String, Object>> outputData, long rowCount, long durationMs) {
        return NodeResult.builder()
                .success(true)
                .outputData(outputData)
                .rowCount(rowCount)
                .durationMs(durationMs)
                .build();
    }

    /**
     * Create a failed result with error message.
     */
    public static NodeResult failure(String errorMessage) {
        return NodeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
