package com.dataviz.common.mybatis.shard;

/**
 * 实例（库）路由维度。
 * <p>
 * {@link #TENANT}：同一个租户的所有行落在同一个实例，{@code routeValue} 传租户号。
 * 选它是为了让"租户内查询"永远只打一个库——跨库 join 和跨库分页在 17 个服务里没有统一收口，
 * 少一次跨库就少一类只在生产出现的 bug。
 * <p>
 * {@link #NONE}：没有业务维度的路由（典型是 {@code openapi_log} 这种无租户明细），分表后缀照用，
 * 实例按下标轮转 {@code floorMod(hash(后缀), buckets)}。
 * <p>
 * <b>为什么不用一致性哈希</b>：桶数一旦从 2 改到 3，一致性哈希以外的所有取模方案都要重分布历史数据；
 * 而 {@code floorMod(hash(tenant), buckets)} 同样要重分布。本项目把"改桶数"定义为离线迁移事件
 * （需要按租户整库搬迁 + 校验行数），所以路由函数刻意保持最笨的形式：可预测、可用一条 SQL 复核
 * （{@code SELECT COUNT(*) FROM x WHERE ...} 按实例数对账）。
 */
public enum ShardRouteBy {

    /** 按租户号路由实例。 */
    TENANT,

    /** 无业务路由维度（只分表，或按时间后缀轮转实例）。 */
    NONE
}
