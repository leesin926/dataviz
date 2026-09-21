package com.dataviz.etl.vo;

import lombok.Data;

/**
 * An edge (connection) between two DAG nodes.
 */
@Data
public class DagEdge {
    /** Source node ID */
    private String source;
    /** Target node ID */
    private String target;
    /** Optional label for the edge */
    private String label;
}
