package com.dataviz.common.mybatis.shard;

/**
 * 分片时间粒度：决定物理表后缀的形状。
 * <p>
 * {@link #MONTH} 适合按自然月增长的追加型表（{@code _202611}），{@link #DAY} 适合量级更大的
 * 日志（{@code _20261106}），{@link #NONE} 表示"只分库不分表"——后缀恒为空串，实例选择仍按
 * {@link ShardRouteBy} 走。
 * <p>
 * 枚举名在 yml 里原样写（{@code mode: MONTH}），Spring 的宽松绑定会把 {@code month} 也认下来。
 * 新增粒度必须同时改 {@link ShardRouter#suffix} 与 {@link ShardRouter#targetsOf} 的区间枚举，
 * 否则会出现"能配出来但读不到"的表。
 */
public enum ShardMode {

    /** 按月分表：后缀 {@code _yyyyMM}。 */
    MONTH,

    /** 按日分表：后缀 {@code _yyyyMMdd}。 */
    DAY,

    /** 不分表：后缀恒为空串，仅按 {@link ShardRouteBy} 选实例。 */
    NONE
}
