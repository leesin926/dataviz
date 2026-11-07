package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 区间读的扇出执行器：按计划逐个分片绑定上下文 → 跑调用方给的取数函数 → k 路归并出全局 offset/limit。
 * <p>
 * 单线程顺序执行，<b>不并发</b>。理由不是偷懒：{@link ShardContext} 是 ThreadLocal，并发扇出要先把绑定态
 * 搬到 worker 线程并保证每个线程各自一份，出错时"哪个线程写错库"会比现在难查得多。真要提吞吐，
 * 正确的位置是在这一层外面加有界线程池 + 显式传参的上下文对象，而不是把 ThreadLocal 隐式跨线程传。
 *
 * @see ShardRouter#targetsWithin(String, Object, Object, Object, boolean) 计划来源
 */
@Slf4j
public class ShardQueryFanout {

    private final ShardRouter router;

    public ShardQueryFanout(ShardRouter router) {
        this.router = router;
    }

    /**
     * 租户内区间分页读：只扫该租户所在实例上的相关分片。
     * <p>
     * {@code perShardQuery} 的<b>契约</b>（必须遵守，否则结果不完整）：
     * 在该分片上按 {@code comparator} 同序取<b>前 {@code offset + limit} 行</b>（即 SQL 里
     * {@code ORDER BY <同一列> LIMIT (offset+limit)}），而不是 {@code LIMIT offset, limit}——
     * 分片内分页与全局分页不等价。
     */
    public <R> ShardResult<R> read(String table, Object tenantId, Object from, Object to,
                                   int offset, int limit, Comparator<R> comparator,
                                   Function<ShardTarget, List<R>> perShardQuery) {
        return doRead(table, tenantId, true, from, to, offset, limit, comparator, perShardQuery);
    }

    /** 跨租户区间分页读：该表全部 buckets 实例 × 时间区间都扫。 */
    public <R> ShardResult<R> readAcrossTenants(String table, Object from, Object to,
                                                int offset, int limit, Comparator<R> comparator,
                                                Function<ShardTarget, List<R>> perShardQuery) {
        return doRead(table, null, false, from, to, offset, limit, comparator, perShardQuery);
    }

    /**
     * 同 {@link #read}，但把每片应取的行数（{@code offset + limit}）交给调用方，省得调用方自己再算一遍
     * ——写错一次就是"翻页跳到错位置"。
     */
    public <R> ShardResult<R> readCapped(String table, Object tenantId, Object from, Object to,
                                         int offset, int limit, Comparator<R> comparator,
                                         ShardRowFetcher<R> fetcher) {
        final int fetchSize = fetchSize(offset, limit);
        final ShardRowFetcher<R> perShard = fetcher;
        return doRead(table, tenantId, true, from, to, offset, limit, comparator,
                new Function<ShardTarget, List<R>>() {
                    @Override
                    public List<R> apply(ShardTarget target) {
                        try {
                            return perShard.fetch(target, fetchSize);
                        } catch (Exception e) {
                            throw new BizException(ErrorCode.BAD_REQUEST,
                                    "分片 " + target + " 取数失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                });
    }

    /** 租户内区间计数：各分片计数求和。 */
    public ShardCount countAll(String table, Object tenantId, Object from, Object to,
                               ToLongFunction<ShardTarget> perShardCount) {
        return doCount(table, tenantId, true, from, to, perShardCount);
    }

    /** 跨租户区间计数：全部实例求和。 */
    public ShardCount countAllAcrossTenants(String table, Object from, Object to,
                                            ToLongFunction<ShardTarget> perShardCount) {
        return doCount(table, null, false, from, to, perShardCount);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private <R> ShardResult<R> doRead(String table, Object routeValue, boolean routeScoped, Object from, Object to,
                                      int offset, int limit, Comparator<R> comparator,
                                      Function<ShardTarget, List<R>> perShardQuery) {
        requirePaging(offset, limit);
        if (comparator == null) {
            throw new IllegalArgumentException("comparator 不能为空：归并必须有确定的全局序");
        }
        if (perShardQuery == null) {
            throw new IllegalArgumentException("perShardQuery 不能为空");
        }
        ShardRouter.Scan scan = router.targetsWithin(table, routeValue, from, to, routeScoped);
        int fetchSize = fetchSize(offset, limit);

        List<R> merged = new ArrayList<R>();
        int scannedRows = 0;
        for (ShardTarget target : scan.getTargets()) {
            // 每个分片一次绑定、一次恢复：嵌套扇出（外层已有绑定）也不会串味
            try (ShardContext.ShardScope scope = ShardContext.open(target)) {
                List<R> rows = perShardQuery.apply(target);
                if (rows == null || rows.isEmpty()) {
                    continue;
                }
                // 防御性截断：调用方多取了对本页没有意义，少取则结果不完整（只能靠契约保证）
                int usable = Math.min(rows.size(), fetchSize);
                scannedRows += rows.size();
                merged.addAll(rows.subList(0, usable));
            }
        }
        Collections.sort(merged, comparator);
        // 全局窗口 = 归并后 skip offset、take limit。每片必须取够 offset+limit 行：全局前 offset+limit 名
        // 有可能全部来自同一个分片，每片只取 limit 行时 offset>0 的页会从错误位置开始（这是这里经典的 off-by-N）。
        int from2 = Math.min(offset, merged.size());
        int to2 = Math.min(offset + limit, merged.size());
        List<R> page = new ArrayList<R>(merged.subList(from2, to2));
        boolean more = merged.size() > offset + limit;
        if (scan.isTruncated()) {
            log.warn("分片表 {} 区间读被 shard.max-shards-per-read={} 截断，本结果只覆盖 {} 个分片（需要 {} 个）",
                    table, scan.getCap(), scan.getTargets().size(), scan.getRequiredShards());
        }
        return new ShardResult<R>(page, scan.getTargets().size(), scan.isTruncated(), offset, limit,
                scannedRows, more, page.size() < limit && !more);
    }

    private ShardCount doCount(String table, Object routeValue, boolean routeScoped, Object from, Object to,
                               ToLongFunction<ShardTarget> perShardCount) {
        if (perShardCount == null) {
            throw new IllegalArgumentException("perShardCount 不能为空");
        }
        ShardRouter.Scan scan = router.targetsWithin(table, routeValue, from, to, routeScoped);
        long total = 0L;
        for (ShardTarget target : scan.getTargets()) {
            try (ShardContext.ShardScope scope = ShardContext.open(target)) {
                total += perShardCount.applyAsLong(target);
            }
        }
        if (scan.isTruncated()) {
            log.warn("分片表 {} 区间计数被 shard.max-shards-per-read={} 截断：总数少算了 {} 个分片，结果不完整",
                    table, scan.getCap(), scan.getRequiredShards() - scan.getTargets().size());
        }
        return new ShardCount(total, scan.getTargets().size(), scan.isTruncated(), scan.getRequiredShards());
    }

    private static void requirePaging(int offset, int limit) {
        if (offset < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分片分页 offset 不能为负: " + offset);
        }
        if (limit < 1) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "分片分页 limit 必须 ≥ 1（要全量请用分批循环，不要一次拉穿内存）: " + limit);
        }
    }

    /** 每片取数上限：超过 int 直接拒绝，不静默截断成小值。 */
    private static int fetchSize(int offset, int limit) {
        long needed = (long) offset + (long) limit;
        if (needed > Integer.MAX_VALUE) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "offset + limit 超出 int 范围: " + needed + "，请用游标式分页替代深翻页");
        }
        return (int) needed;
    }

    /** 每片带行数的取数回调（checked exception 由外层统一包成 BizException）。 */
    public interface ShardRowFetcher<R> {
        List<R> fetch(ShardTarget target, int maxRows) throws Exception;
    }

    /** 一页分片归并结果。{@code truncated} 为真时前端必须显示"结果不完整"，不能当全部用。 */
    @Getter
    public static final class ShardResult<R> {

        private final List<R> records;
        /** 实际扫描的分片数。 */
        private final int shards;
        private final boolean truncated;
        private final int offset;
        private final int limit;
        /** 各分片返回的原始行数（截断到 fetchSize 之前累计）。 */
        private final int scannedRows;
        /** 归并后窗口之后还有行：可用于"下一页"按钮，注意 {@code truncated} 时这个信号不可靠。 */
        private final boolean more;
        /** 已扫完且当前页不满：只在 {@code truncated=false} 时有意义。 */
        private final boolean exhausted;

        ShardResult(List<R> records, int shards, boolean truncated, int offset, int limit,
                    int scannedRows, boolean more, boolean exhausted) {
            this.records = records;
            this.shards = shards;
            this.truncated = truncated;
            this.offset = offset;
            this.limit = limit;
            this.scannedRows = scannedRows;
            this.more = more;
            this.exhausted = exhausted;
        }

        public List<R> getRecords() {
            return records;
        }

        @Override
        public String toString() {
            return "ShardResult{size=" + records.size() + ", shards=" + shards
                    + ", truncated=" + truncated + ", offset=" + offset + ", limit=" + limit
                    + ", scannedRows=" + scannedRows + ", more=" + more + "}";
        }
    }

    /** 分片计数结果。截断时 {@code total} 是<b>下界</b>，不是总数。 */
    @Getter
    public static final class ShardCount {

        private final long total;
        private final int shards;
        private final boolean truncated;
        private final int requiredShards;

        ShardCount(long total, int shards, boolean truncated, int requiredShards) {
            this.total = total;
            this.shards = shards;
            this.truncated = truncated;
            this.requiredShards = requiredShards;
        }

        public long getTotal() {
            return total;
        }

        @Override
        public String toString() {
            return "ShardCount{total=" + total + ", shards=" + shards + ", truncated=" + truncated + "}";
        }
    }
}
