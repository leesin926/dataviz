package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

import java.util.List;

/**
 * 分片层错误文案的唯一出处。
 * <p>
 * 集中放这里只有一个原因：{@link ShardInnerInterceptor}（SQL 层）和 {@link ShardRoutingDataSource}
 * （连接层）对同一个错误的拒绝<b>必须是同一句话</b>。两句话的话，运维会以为是两个不同的故障，
 * 排查时把同一条链路查两遍。
 * <p>
 * 纪律：任何消息里都不得出现实例 url、用户名、口令——这些异常会进日志、进响应体。
 */
final class ShardErrors {

    private ShardErrors() {
    }

    /**
     * 命中了分片表但没有绑定路由上下文。
     * <p>
     * 消息必须点名两张事实：租户维度与时间列——调用方补的就是这两个值，写清楚比写"上下文缺失"有用。
     */
    static BizException unboundContext(String logicalTable, ShardProperties.Rule rule) {
        String routeHint = rule == null || rule.getRouteBy() == ShardRouteBy.TENANT
                ? "tenant_id（当前租户号，非空）"
                : "时间值（用于实例轮转）";
        String timeHint = rule == null ? "时间列的值" : rule.getTimeColumn() + "（分片时间粒度 "
                + (rule.getMode() == null ? "UNKNOWN" : rule.getMode().name()) + "）";
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 执行前未绑定路由上下文，拒绝执行（不会退回默认实例：静默写错库"
                        + "是本层最难查的缺陷）。请调用 ShardContext.open(\"" + logicalTable + "\", ...) 绑定 "
                        + routeHint + " 与 " + timeHint + " 两项。");
    }

    /** 绑定了上下文但目标实例无法确定（规则与实例清单不一致）。 */
    static BizException unresolvedInstance(String logicalTable, String detail) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 无法确定目标实例：" + detail);
    }

    /** 上下文绑定了分片表，但路由值为空。 */
    static BizException blankRouteValue(String logicalTable, String valueName) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 的 " + valueName + " 为空，拒绝路由（空租户写进去就再也按租户捞不回来了）");
    }

    /** 路由值类型不受支持：POJO/数组的字符串形式带身份哈希，重启后同一租户会散到不同实例，等于数据丢失。 */
    static BizException unstableRouteValue(Object routeValue) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片路由值类型不支持: " + (routeValue == null ? "null" : routeValue.getClass().getName())
                        + "。只接受 String/Number/Character——其余类型的字符串形式带身份哈希"
                        + "（默认 toString() 里的 1a2b3c），同一条记录会在重启后落到不同实例。"
                        + "请显式转成字符串（如 tenantId.toString()）。");
    }

    /** 时间值类型不受支持。 */
    static BizException unsupportedTimeValue(Object timeValue, String logicalTable) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 的时间值类型不支持: "
                        + (timeValue == null ? "null" : timeValue.getClass().getName())
                        + "（值=" + timeValue + "）。接受 LocalDateTime/LocalDate/java.util.Date/"
                        + "java.sql.Timestamp 或 ISO 风格字符串。");
    }

    /** 区间读扇出超限。 */
    static BizException tooManyShards(String logicalTable, int needed, int maxShards) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 本次区间读需要扫 " + needed + " 个物理分片，超过上限 shard.max-shards-per-read="
                        + maxShards + "。请缩小时间范围，或确认代价后调大 shard.max-shards-per-read"
                        + "（放开上限等于允许一次十年跨度的全量扇出，页面会挂死）。");
    }

    /** 拼出来的物理表名不合法——后缀只能来自本层生成，不接受外部传入的任意字符串。 */
    static BizException illegalPhysicalName(String logicalTable, String suffix) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "分片表 " + logicalTable + " 的分片后缀不合法: \"" + suffix
                        + "\"。后缀只能由 ShardRouter.suffix 生成（形如 _202611 / _20261106）。");
    }

    /**
     * 一条语句里出现多张分片表。
     * <p>
     * 本层不支持：一张语句只有一个 {@code (租户, 时间)} 绑定，两张分片表就需要笛卡尔式的后缀组合，
     * 拼出来的 SQL 会跨月跨库 join。让它当场失败，比让它悄悄返回错数据好。
     */
    static BizException multipleShardedTables(String sql, List<String> tables) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "一条语句命中多张分片表 " + tables + "，分片层不支持跨分片 join，请拆成两次查询在内存里聚合。"
                        + "（配置/CRUD 表与分片表 join 不受此限。）");
    }
}
