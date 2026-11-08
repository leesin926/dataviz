package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.common.mybatis.shard.ShardTarget;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code alert_event} 的分片读路径（{@code shard.enabled=true} 时才会被用到）。形状照
 * {@code openapi} 那条 {@code OpenApiLogShardReader} 抄，因为那一条是在真进程里被运行时纠正过之后
 * 才定下来的（API-171 / API-173），这里直接继承它的结论，不再重犯一次：
 *
 * <ol>
 *   <li><b>行查询与计数各一个 wrapper。</b> 把带 {@code select(...)} 投影的 wrapper 交给
 *       {@code selectCount}，MP 会生成 {@code COUNT(id, rule_id, ...)} —— MySQL 1064，整页 500。
 *       分页插件自己生成 count SQL 时会剥投影，扇出是我们自己发第二条 SQL，没人剥。</li>
 *   <li><b>每片取前 {@code offset+limit} 行</b>，不是 {@code LIMIT offset,limit}：全局前 N 名可能全来自
 *       同一片，按片内分页翻页会从错位置开始，而返回的行看起来完全正常。</li>
 *   <li><b>归并序与片内序必须同一条</b>（{@link #NEWEST_FIRST}），且带 {@code id} 兜底 ——
 *       同一秒触发多条事件的告警很常见，没有兜底键翻页就会重行/漏行。</li>
 *   <li><b>{@code route-by=TENANT} ⇒ 走 {@link ShardQueryFanout#read}/{@link ShardQueryFanout#countAll}
 *       这一对（租户内），不是 openapi 那条 {@code *AcrossTenants}。</b> 一张表的行落在哪个实例由租户哈希
 *       决定，所以带租户的读天然只打一个实例，扫的是这个实例上区间内的各张月表。</li>
 * </ol>
 */
@RequiredArgsConstructor
class AlertEventShardReader {

    /** 逻辑表名必须与 {@code shard.rules} 与 {@code @TableName("alert_event")} 三处一致 */
    static final String TABLE = "alert_event";

    static final Comparator<AlertEvent> NEWEST_FIRST = Comparator
            .comparing(AlertEvent::getCreateTime, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(AlertEvent::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ShardQueryFanout fanout;
    private final AlertEventMapper eventMapper;

    PageOutcome page(Supplier<LambdaQueryWrapper<AlertEvent>> rowWrapperFactory,
                     Supplier<LambdaQueryWrapper<AlertEvent>> countWrapperFactory,
                     Object tenantId, LocalDateTime from, LocalDateTime to, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;

        ShardQueryFanout.ShardResult<AlertEvent> rows = fanout.read(TABLE, tenantId, from, to,
                offset, pageSize, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<AlertEvent>>() {
                    @Override
                    public List<AlertEvent> apply(ShardTarget target) {
                        Page<AlertEvent> shardPage = new Page<AlertEvent>(1, offset + pageSize);
                        shardPage.setSearchCount(false);
                        return eventMapper.selectPage(shardPage, rowWrapperFactory.get()).getRecords();
                    }
                });

        CountOutcome counted = count(countWrapperFactory, tenantId, from, to);
        // 两条扇出都可能被上限截断，任一条截断这一页就不完整 —— 只报其中一条会低估"少了多少"
        return new PageOutcome(rows.getRecords(), counted.getTotal(),
                rows.isTruncated() || counted.isTruncated(),
                Math.max(rows.getShards(), counted.getShards()));
    }

    /** 区间计数。截断时 {@code total} 只是下界 —— 调用方必须把这件事传出去。 */
    CountOutcome count(Supplier<LambdaQueryWrapper<AlertEvent>> countWrapperFactory,
                       Object tenantId, LocalDateTime from, LocalDateTime to) {
        ShardQueryFanout.ShardCount c = fanout.countAll(TABLE, tenantId, from, to,
                new java.util.function.ToLongFunction<ShardTarget>() {
                    @Override
                    public long applyAsLong(ShardTarget target) {
                        Long n = eventMapper.selectCount(countWrapperFactory.get());
                        return n == null ? 0L : n;
                    }
                });
        return new CountOutcome(c.getTotal(), c.isTruncated(), c.getShards());
    }

    /**
     * 按主键定位一条事件。
     * <p>
     * 裸 {@code id} 定不出月表（自增主键与分表键无关），所以这是一个<b>有界扫描</b>：只在调用方给的窗口里
     * 逐张月表查该 id。窗口的来源与代价写在 {@code AlertEventServiceImpl#locate}。
     */
    AlertEvent locate(long id, Object tenantId, LocalDateTime from, LocalDateTime to) {
        ShardQueryFanout.ShardResult<AlertEvent> one = fanout.read(TABLE, tenantId, from, to,
                0, 1, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<AlertEvent>>() {
                    @Override
                    public List<AlertEvent> apply(ShardTarget target) {
                        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>();
                        wrapper.eq(AlertEvent::getId, id);
                        List<AlertEvent> rows = eventMapper.selectList(wrapper);
                        return rows == null ? Collections.<AlertEvent>emptyList() : rows;
                    }
                });
        List<AlertEvent> records = one.getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    /** 一页的扇出产物。 */
    @Getter
    @RequiredArgsConstructor
    static class PageOutcome {
        private final List<AlertEvent> records;
        private final long total;
        private final boolean truncated;
        private final int shards;
    }

    /** 一次区间计数的扇出产物。 */
    @Getter
    @RequiredArgsConstructor
    static class CountOutcome {
        private final long total;
        private final boolean truncated;
        private final int shards;
    }
}
