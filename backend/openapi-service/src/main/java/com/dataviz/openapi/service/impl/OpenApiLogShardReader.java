package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiLogMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code openapi_log} 的分片读路径：一次分页 = 逐月表各取前 {@code offset+limit} 行 → 归并 → 取全局窗口，
 * 计数 = 逐月表求和。只有在 {@code shard.enabled=true}（容器里有 {@link ShardQueryFanout}）时才会被用到，
 * 没开分片时 {@code ApiLogServiceImpl} 走的仍是原来那条单表查询。
 *
 * <h3>为什么每片要取 offset+limit 而不是 offset,limit</h3>
 * <p>
 * 全局前 {@code offset+limit} 名有可能全部来自同一个分片。按分片内分页写法（每片 {@code LIMIT offset,limit}）
 * 翻到第二页时会从错误的位置开始 —— 返回的行看起来完全正常，只是中间少了一段，这类错在页面上看不出来。
 *
 * <h3>为什么 wrapper 每片新建一个、而且行与计数各一个</h3>
 * <p>
 * MP 的分页插件会往 wrapper 上挂 ORDER BY / count SQL 的中间状态，同一个实例喂给多个分片会互相污染；
 * 而且扇出的语义是"每片一条独立 SQL"，构造独立对象才是它的真实形状。
 * 行查询带列白名单和排序，计数只带 WHERE —— 把带投影的 wrapper 交给 {@code selectCount}，
 * MP 会生成 {@code COUNT(id, client_id, ...)}（MySQL 1064）。
 */
@RequiredArgsConstructor
class OpenApiLogShardReader {

    /** 逻辑表名必须与 {@code shard.rules} 里那条一致，也必须与 {@code @TableName("openapi_log")} 一致。 */
    static final String TABLE = "openapi_log";

    /** 与 SQL 侧 {@code ORDER BY create_time DESC, id DESC} 同序；归并的序与分片内的序不能各说各话。 */
    static final Comparator<OpenApiLog> NEWEST_FIRST = Comparator
            .comparing(OpenApiLog::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(OpenApiLog::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ShardQueryFanout fanout;
    private final OpenApiLogMapper logMapper;

    ShardOutcome page(Supplier<LambdaQueryWrapper<OpenApiLog>> rowWrapperFactory,
                      Supplier<LambdaQueryWrapper<OpenApiLog>> countWrapperFactory,
                      LocalDateTime from, LocalDateTime to, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;

        ShardQueryFanout.ShardResult<OpenApiLog> rows = fanout.readAcrossTenants(TABLE, from, to,
                offset, pageSize, NEWEST_FIRST,
                new java.util.function.Function<com.dataviz.common.mybatis.shard.ShardTarget, List<OpenApiLog>>() {
                    @Override
                    public List<OpenApiLog> apply(com.dataviz.common.mybatis.shard.ShardTarget target) {
                        Page<OpenApiLog> shardPage = new Page<OpenApiLog>(1, offset + pageSize);
                        // 计数由下面那条求和负责，不要在每片再各跑一次 count（那是 2N 条 SQL）
                        shardPage.setSearchCount(false);
                        return logMapper.selectPage(shardPage, rowWrapperFactory.get()).getRecords();
                    }
                });

        ShardQueryFanout.ShardCount count = fanout.countAllAcrossTenants(TABLE, from, to,
                new java.util.function.ToLongFunction<com.dataviz.common.mybatis.shard.ShardTarget>() {
                    @Override
                    public long applyAsLong(com.dataviz.common.mybatis.shard.ShardTarget target) {
                        // 必须是 countWrapperFactory：投影进 COUNT(...) 就是 COUNT(a, b)，MySQL 当场 1064
                        Long rows = logMapper.selectCount(countWrapperFactory.get());
                        return rows == null ? 0L : rows;
                    }
                });

        // 两条扇出都可能被上限截断，任一条截断这一页就不完整 —— 只报其中一条会低估"少了多少"
        return new ShardOutcome(rows.getRecords(), count.getTotal(),
                rows.isTruncated() || count.isTruncated(), rows.getShards(), count.getShards());
    }

    /** 扇出一次分页的产物：本页行 + 全局总数 + 两个截断/规模读数。 */
    @Getter
    @RequiredArgsConstructor
    static class ShardOutcome {
        private final List<OpenApiLog> records;
        private final long total;
        private final boolean truncated;
        private final int scannedShards;
        private final int countedShards;
    }
}
