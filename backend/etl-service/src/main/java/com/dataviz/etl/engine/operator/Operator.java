package com.dataviz.etl.engine.operator;

/**
 * Operator interface for ETL DAG node execution.
 * <p>
 * Each operator type (database input, filter, SQL, aggregate, etc.)
 * implements this interface to provide its specific execution logic.
 * </p>
 */
public interface Operator {

    /**
     * Execute the operator logic with the given context.
     *
     * @param context the node execution context containing config, input data, and shared state
     * @return the result of the node execution
     */
    NodeResult execute(NodeContext context);

    /**
     * Get the operator type identifier.
     */
    String getType();
}
