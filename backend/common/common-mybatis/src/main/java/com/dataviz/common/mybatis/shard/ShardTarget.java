package com.dataviz.common.mybatis.shard;

import java.time.LocalDateTime;

/**
 * 一次扇出读的最小可寻址单位：一个实例上的一个物理表。
 * <p>
 * 不可变。{@code timeFrom/timeTo} 是该分片覆盖的时间边界，供调用方在分片内做二次收窄
 * （首/尾分片往往只覆盖区间的一部分）；{@code NONE} 模式下两者为空。
 */
public final class ShardTarget {

    private final String logicalTable;
    private final String suffix;
    private final String instanceId;
    private final int instanceIndex;
    private final LocalDateTime timeFrom;
    private final LocalDateTime timeTo;

    public ShardTarget(String logicalTable, String suffix, String instanceId, int instanceIndex,
                       LocalDateTime timeFrom, LocalDateTime timeTo) {
        this.logicalTable = logicalTable;
        this.suffix = suffix == null ? "" : suffix;
        this.instanceId = instanceId;
        this.instanceIndex = instanceIndex;
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
    }

    public String getLogicalTable() {
        return logicalTable;
    }

    /** 形如 {@code _202611}；{@code mode=NONE} 时为空串。 */
    public String getSuffix() {
        return suffix;
    }

    /** 实例 id，即 {@link ShardRoutingDataSource} 的路由键。 */
    public String getInstanceId() {
        return instanceId;
    }

    public int getInstanceIndex() {
        return instanceIndex;
    }

    /** 该分片覆盖区间起（含）；{@code mode=NONE} 时为 null。 */
    public LocalDateTime getTimeFrom() {
        return timeFrom;
    }

    /** 该分片覆盖区间止（不含）；{@code mode=NONE} 时为 null。 */
    public LocalDateTime getTimeTo() {
        return timeTo;
    }

    public String getPhysicalTable() {
        return ShardRouter.physical(logicalTable, suffix);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShardTarget)) {
            return false;
        }
        ShardTarget that = (ShardTarget) other;
        return instanceIndex == that.instanceIndex
                && equalsOrEmpty(logicalTable, that.logicalTable)
                && equalsOrEmpty(suffix, that.suffix)
                && equalsOrEmpty(instanceId, that.instanceId);
    }

    private static boolean equalsOrEmpty(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        int result = logicalTable == null ? 0 : logicalTable.hashCode();
        result = 31 * result + suffix.hashCode();
        result = 31 * result + instanceIndex;
        return result;
    }

    @Override
    public String toString() {
        return instanceId + "." + getPhysicalTable();
    }
}
