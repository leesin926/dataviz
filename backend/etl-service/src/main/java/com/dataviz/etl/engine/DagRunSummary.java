package com.dataviz.etl.engine;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * What a DAG run actually moved, as reported back to the task log.
 * <p>
 * 记账口径：<b>rowsRead</b> 只累加源端节点读到的行，<b>rowsWritten</b> 只累加目标端节点写入的行。
 * 中间节点的行数不进这里 —— 它们既不是"从库里取了多少"也不是"往库里落了多少"，混进去的话
 * 一个 filter 节点会让同批数据在读取数里出现两次。
 * <p>
 * warnings 是"这一批确实跑完了，但结果不完整"的说明（目前只有一种：源端被行数上限截断）。
 * 这类情况不能只写日志：日志不在任务的历史列表里，看的人只知道"成功"。
 */
@Data
@Builder
public class DagRunSummary {

    /** 源端算子类型：它的 rowCount 记进 rowsRead */
    public static final String SOURCE_TYPE = "database_input";

    /** 目标端算子类型：它的 rowCount 记进 rowsWritten */
    public static final String SINK_TYPE = "database_output";

    /**
     * 源端一次读取的行数天花板，与 datasource-service 内部查询端点的 MAX_ROWS_CEILING 同值。
     * 读满这个数就报"结果可能不完整"：本引擎还没有分页抽取，超上限的源表只会搬走前这么多行。
     */
    public static final int READ_ROW_CEILING = 5000;

    /** Rows pulled by source nodes */
    private long rowsRead;

    /** Rows appended by sink nodes */
    private long rowsWritten;

    /** Nodes that finished */
    private int executedNodes;

    /** Total nodes in the plan */
    private int plannedNodes;

    /** Stop was requested before the plan finished */
    private boolean cancelled;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public boolean incomplete() {
        return cancelled || !warnings.isEmpty();
    }
}
