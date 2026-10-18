package com.dataviz.etl.engine;

/**
 * DAG 执行失败，并带上"失败前已经搬到哪一步"的账。
 * <p>
 * 不带这层信息的话，源端读了 3000 行、目标端写第 2 批时炸掉的执行，落到任务历史里就是
 * 「读取 0 行 / 写入 0 行 + 失败」—— 排查的人看不出目标表里有没有半批数据，也不会去查上游。
 * 计数语义见 {@link DagRunSummary}。
 */
public class DagExecutionException extends RuntimeException {

    private final DagRunSummary partial;

    public DagExecutionException(String message, DagRunSummary partial, Throwable cause) {
        super(message, cause);
        this.partial = partial;
    }

    /** 失败前已完成节点的记账；DAG 在跑出任何节点结果之前就坏了时为 null。 */
    public DagRunSummary getPartial() {
        return partial;
    }
}
