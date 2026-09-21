package com.dataviz.etl.vo;

import lombok.Data;
import java.util.Map;

/**
 * A single node in the DAG.
 */
@Data
public class DagNode {
    /** Unique node identifier */
    private String id;
    /** Operator type (database_input, filter, sql, aggregate, etc.) */
    private String type;
    /** Display name for the node */
    private String name;
    /** Node-specific configuration */
    private Map<String, Object> config;
    /** Position for visual rendering (x coordinate) */
    private Double positionX;
    /** Position for visual rendering (y coordinate) */
    private Double positionY;
}
