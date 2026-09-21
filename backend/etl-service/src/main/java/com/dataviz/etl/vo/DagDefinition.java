package com.dataviz.etl.vo;

import lombok.Data;
import java.util.List;

/**
 * DAG definition containing nodes and edges.
 */
@Data
public class DagDefinition {
    private List<DagNode> nodes;
    private List<DagEdge> edges;
}
