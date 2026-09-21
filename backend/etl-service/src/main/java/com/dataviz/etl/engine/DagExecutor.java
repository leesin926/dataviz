package com.dataviz.etl.engine;

import com.dataviz.etl.engine.operator.NodeContext;
import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import com.dataviz.etl.vo.DagDefinition;
import com.dataviz.etl.vo.DagEdge;
import com.dataviz.etl.vo.DagNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * DAG (Directed Acyclic Graph) executor for ETL tasks.
 * <p>
 * Performs topological sort to determine execution order and
 * executes nodes in parallel where possible. Supports cancellation
 * via instance ID tracking.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagExecutor {

    private final OperatorFactory operatorFactory;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    /** Track running instances for cancellation */
    private final Map<Long, Set<Future<?>>> instanceFutures = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> cancelledInstances = new ConcurrentHashMap<>();

    /**
     * Execute a DAG defined as JSON.
     */
    public void execute(String dagJson, Long instanceId) {
        try {
            DagDefinition dag = objectMapper.readValue(dagJson, DagDefinition.class);
            List<DagNode> nodes = dag.getNodes();
            List<DagEdge> edges = dag.getEdges();

            if (nodes == null || nodes.isEmpty()) {
                throw new IllegalArgumentException("DAG has no nodes");
            }

            // Topological sort
            List<List<DagNode>> levels = topologicalSort(nodes, edges);
            log.info("DAG execution plan: {} levels, {} total nodes", levels.size(), nodes.size());

            // Shared state across all nodes
            Map<String, Object> sharedState = new ConcurrentHashMap<>();
            // Node outputs keyed by node ID
            Map<String, NodeResult> nodeResults = new ConcurrentHashMap<>();

            cancelledInstances.put(instanceId, false);
            instanceFutures.put(instanceId, ConcurrentHashMap.newKeySet());

            // Execute level by level (nodes within the same level run in parallel)
            for (int i = 0; i < levels.size(); i++) {
                if (Boolean.TRUE.equals(cancelledInstances.get(instanceId))) {
                    log.info("DAG execution cancelled at level {}", i);
                    break;
                }

                List<DagNode> level = levels.get(i);
                log.info("Executing level {}: {} nodes", i, level.size());

                List<Future<?>> futures = new ArrayList<>();
                for (DagNode node : level) {
                    Future<?> future = executorService.submit(() -> {
                        if (Boolean.TRUE.equals(cancelledInstances.get(instanceId))) return;

                        Operator operator = operatorFactory.getOperator(node.getType());

                        // Collect input from upstream nodes
                        List<Map<String, Object>> inputData = collectInput(node, edges, nodeResults);

                        NodeContext context = NodeContext.builder()
                                .nodeId(node.getId())
                                .operatorType(node.getType())
                                .config(node.getConfig())
                                .inputData(inputData)
                                .sharedState(sharedState)
                                .instanceId(instanceId)
                                .build();

                        log.info("Executing node: id={}, type={}", node.getId(), node.getType());
                        NodeResult result = operator.execute(context);
                        nodeResults.put(node.getId(), result);

                        if (!result.isSuccess()) {
                            log.error("Node {} failed: {}", node.getId(), result.getErrorMessage());
                            throw new RuntimeException("Node " + node.getId() + " failed: " + result.getErrorMessage());
                        }
                        log.info("Node {} completed: {} rows in {}ms", node.getId(), result.getRowCount(), result.getDurationMs());
                    });
                    futures.add(future);
                    instanceFutures.get(instanceId).add(future);
                }

                // Wait for all nodes in this level to complete
                for (Future<?> future : futures) {
                    try {
                        future.get(30, TimeUnit.MINUTES);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        throw new RuntimeException("Node execution timed out");
                    } catch (ExecutionException e) {
                        throw new RuntimeException("Node execution failed: " + e.getCause().getMessage(), e.getCause());
                    }
                }
            }

            log.info("DAG execution completed for instance: {}", instanceId);

        } catch (Exception e) {
            log.error("DAG execution failed for instance: {}", instanceId, e);
            throw new RuntimeException("DAG execution failed: " + e.getMessage(), e);
        } finally {
            cancelledInstances.remove(instanceId);
            instanceFutures.remove(instanceId);
        }
    }

    /**
     * Stop a running DAG execution.
     */
    public void stop(Long instanceId) {
        cancelledInstances.put(instanceId, true);
        Set<Future<?>> futures = instanceFutures.get(instanceId);
        if (futures != null) {
            futures.forEach(f -> f.cancel(true));
        }
        log.info("DAG execution stop requested for instance: {}", instanceId);
    }

    /**
     * Perform topological sort and group nodes into execution levels.
     */
    private List<List<DagNode>> topologicalSort(List<DagNode> nodes, List<DagEdge> edges) {
        Map<String, DagNode> nodeMap = nodes.stream().collect(Collectors.toMap(DagNode::getId, n -> n));
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        // Initialize
        for (DagNode node : nodes) {
            inDegree.put(node.getId(), 0);
            adjacency.put(node.getId(), new ArrayList<>());
        }

        // Build graph
        if (edges != null) {
            for (DagEdge edge : edges) {
                adjacency.get(edge.getSource()).add(edge.getTarget());
                inDegree.merge(edge.getTarget(), 1, Integer::sum);
            }
        }

        // BFS-based topological sort by levels
        List<List<DagNode>> levels = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        // Start with nodes having no incoming edges
        inDegree.forEach((id, degree) -> {
            if (degree == 0) queue.add(id);
        });

        while (!queue.isEmpty()) {
            List<DagNode> currentLevel = new ArrayList<>();
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String id = queue.poll();
                currentLevel.add(nodeMap.get(id));
                for (String neighbor : adjacency.get(id)) {
                    inDegree.merge(neighbor, -1, Integer::sum);
                    if (inDegree.get(neighbor) == 0) {
                        queue.add(neighbor);
                    }
                }
            }
            levels.add(currentLevel);
        }

        // Verify no cycles
        int totalProcessed = levels.stream().mapToInt(List::size).sum();
        if (totalProcessed != nodes.size()) {
            throw new IllegalArgumentException("DAG contains a cycle");
        }

        return levels;
    }

    /**
     * Collect input data from upstream nodes based on edges.
     */
    private List<Map<String, Object>> collectInput(DagNode node, List<DagEdge> edges,
                                                     Map<String, NodeResult> nodeResults) {
        if (edges == null || edges.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> combined = new ArrayList<>();
        for (DagEdge edge : edges) {
            if (edge.getTarget().equals(node.getId())) {
                NodeResult upstream = nodeResults.get(edge.getSource());
                if (upstream != null && upstream.getOutputData() != null) {
                    combined.addAll(upstream.getOutputData());
                }
            }
        }
        return combined;
    }
}
