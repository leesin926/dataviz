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
     *
     * @param instanceId run key used for cancellation; ETL passes the task id so 「停止」 hits the running task
     * @return what the run moved, in the accounting sense described by {@link DagRunSummary}
     */
    public DagRunSummary execute(String dagJson, Long instanceId) {
        // 失败时要把"跑过哪些节点、搬了多少行"带出去，所以这几个累加器声明在 try 外面
        Map<String, String> nodeTypes = new HashMap<>();
        // Node outputs keyed by node ID
        Map<String, NodeResult> nodeResults = new ConcurrentHashMap<>();
        // Shared state across all nodes
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        int plannedNodes = 0;
        boolean cancelled = false;
        try {
            DagDefinition dag = objectMapper.readValue(dagJson, DagDefinition.class);
            List<DagNode> nodes = dag.getNodes();
            List<DagEdge> edges = dag.getEdges();

            if (nodes == null || nodes.isEmpty()) {
                throw new IllegalArgumentException("DAG has no nodes");
            }
            plannedNodes = nodes.size();

            // Topological sort
            List<List<DagNode>> levels = topologicalSort(nodes, edges);
            log.info("DAG execution plan: {} levels, {} total nodes", levels.size(), nodes.size());

            for (DagNode node : nodes) {
                nodeTypes.put(node.getId(), node.getType());
            }

            cancelledInstances.put(instanceId, false);
            instanceFutures.put(instanceId, ConcurrentHashMap.newKeySet());

            // Execute level by level (nodes within the same level run in parallel)
            for (int i = 0; i < levels.size() && !cancelled; i++) {
                if (Boolean.TRUE.equals(cancelledInstances.get(instanceId))) {
                    log.info("DAG execution cancelled at level {}", i);
                    cancelled = true;
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
                    } catch (CancellationException e) {
                        // 「停止」按钮先置 cancelledInstances 再 cancel(true)，这里收到的就是这个异常。
                        // 当成失败的话，一次正常的人工停止会变成"任务异常"，还把已读到的行数抹成 0。
                        log.info("DAG execution cancelled while waiting for level {}", i);
                        cancelled = true;
                        break;
                    }
                }
            }

            // 最后一层执行期间才按下的停止不会让上面的 break 生效，所以停止状态在这里补判一次
            boolean stopped = cancelled || Boolean.TRUE.equals(cancelledInstances.get(instanceId));
            log.info("DAG execution finished for instance: {}, cancelled={}", instanceId, stopped);
            return summarize(nodeTypes, nodeResults, plannedNodes, stopped);

        } catch (Exception e) {
            log.error("DAG execution failed for instance: {}", instanceId, e);
            DagRunSummary partial = nodeResults.isEmpty()
                    ? null : summarize(nodeTypes, nodeResults, plannedNodes, cancelled);
            throw new DagExecutionException("DAG execution failed: " + e.getMessage(), partial, e);
        } finally {
            cancelledInstances.remove(instanceId);
            instanceFutures.remove(instanceId);
        }
    }

    /**
     * 记账：源端节点读到的行进 rowsRead，目标端节点写入的行进 rowsWritten，中间节点两边都不进。
     * <p>
     * 只数首尾是因为这两个数会被写进任务历史当成"这一批搬了多少"。把 filter 之类的中间行数也
     * 累进去，同一批数据会在读取数里出现两次，历史列表里的读数就再也没法和库里的行数对上。
     */
    private DagRunSummary summarize(Map<String, String> nodeTypes,
                                    Map<String, NodeResult> nodeResults,
                                    int plannedNodes,
                                    boolean cancelled) {
        long rowsRead = 0;
        long rowsWritten = 0;
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, NodeResult> entry : nodeResults.entrySet()) {
            NodeResult result = entry.getValue();
            String canonical = operatorFactory.canonicalType(nodeTypes.get(entry.getKey()));
            if (DagRunSummary.SOURCE_TYPE.equals(canonical)) {
                rowsRead += result.getRowCount();
                if (isTruncated(result)) {
                    warnings.add("源端节点 " + entry.getKey() + " 一次读取到上限 " + DagRunSummary.READ_ROW_CEILING
                            + " 行，源表可能还有数据没搬完（本引擎暂未分页抽取）");
                }
            } else if (DagRunSummary.SINK_TYPE.equals(canonical)) {
                rowsWritten += result.getRowCount();
            }
        }

        return DagRunSummary.builder()
                .rowsRead(rowsRead)
                .rowsWritten(rowsWritten)
                .executedNodes(nodeResults.size())
                .plannedNodes(plannedNodes)
                .cancelled(cancelled)
                .warnings(warnings)
                .build();
    }

    private boolean isTruncated(NodeResult result) {
        Map<String, Object> metadata = result.getMetadata();
        return metadata != null && Boolean.TRUE.equals(metadata.get("truncated"));
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
