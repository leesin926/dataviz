package com.dataviz.common.mybatis.shard;

import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 扇出执行的判据：归并后的窗口必须等于"全局序上的 offset/limit"。
 * <p>
 * 这里最容易写错、也最难在真机上发现的是<b>每片取 LIMIT offset, limit</b>：分片内偏移不等于全局偏移，
 * 翻到第二页时会从错误位置开始，返回的行看起来完全正常，只是少了中间一段。所以断言里同时钉
 * 每片收到的 maxRows（=offset+limit）与归并后的窗口。
 */
class ShardQueryFanoutTest {

    private static final Comparator<Integer> NEWEST_FIRST = Comparator.reverseOrder();

    private final ShardRouter router = ShardTestFixtures.router("alert_event");
    private final ShardQueryFanout fanout = new ShardQueryFanout(router);

    @AfterEach
    void clearContext() {
        ShardContext.clear();
    }

    @Test
    @DisplayName("三个月分片归并后按全局序取首页：offset=0 limit=2")
    void should_mergeShardsByGlobalOrder_when_firstPage() {
        List<Integer> page = fanout.read("alert_event", 903L, "2026-09-01", "2026-11-30",
                0, 2, NEWEST_FIRST, byMonth(Arrays.asList(11, 10), Arrays.asList(9, 8), Arrays.asList(7, 6)))
                .getRecords();

        assertThat(page).containsExactly(11, 10);
    }

    @Test
    @DisplayName("第二页从归并后的位置继续，而不是从某个分片的中间继续")
    void should_continueGlobalWindow_when_secondPage() {
        ShardQueryFanout.ShardResult<Integer> result = fanout.read("alert_event", 903L,
                "2026-09-01", "2026-11-30", 2, 3, NEWEST_FIRST,
                byMonth(Arrays.asList(11, 10), Arrays.asList(9, 8), Arrays.asList(7, 6)));

        assertThat(result.getRecords()).containsExactly(9, 8, 7);
        assertThat(result.getShards()).isEqualTo(3);
        assertThat(result.getOffset()).isEqualTo(2);
        assertThat(result.getLimit()).isEqualTo(3);
        assertThat(result.isMore()).isTrue();
        assertThat(result.isExhausted()).isFalse();
        assertThat(result.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("每片只取 offset+limit 行，多给的行被丢掉（不给整页内存埋雷）")
    void should_capRowsPerShard_when_fetcherGivesMoreThanNeeded() {
        ShardQueryFanout.ShardResult<Integer> result = fanout.read("alert_event", 903L,
                "2026-09-01", "2026-11-30", 1, 2, NEWEST_FIRST,
                byMonth(tenRows(90), tenRows(80), tenRows(70)));

        // 每片给了 10 行，但本页只需要前 3 行（offset+limit）：第 4 行起不进 merged
        assertThat(result.getRecords()).containsExactly(89, 88);
        assertThat(result.getScannedRows()).isEqualTo(30);
    }

    @Test
    @DisplayName("readCapped 把每片行数上限交给调用方：SQL 里的 LIMIT 就是 offset+limit")
    void should_handFetchSizeToFetcher_when_readCapped() {
        final List<Integer> seenCaps = new ArrayList<Integer>();

        ShardQueryFanout.ShardResult<Integer> result = fanout.readCapped("alert_event", 903L,
                "2026-11-01", "2026-11-30", 0, 10, NEWEST_FIRST,
                new ShardQueryFanout.ShardRowFetcher<Integer>() {
                    @Override
                    public List<Integer> fetch(ShardTarget target, int maxRows) {
                        seenCaps.add(maxRows);
                        return Arrays.asList(30, 29, 28);
                    }
                });

        assertThat(seenCaps).containsExactly(10);
        assertThat(result.getRecords()).containsExactly(30, 29, 28);
        assertThat(result.isMore()).isFalse();
        assertThat(result.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("取数函数里看到的绑定态就是当前分片：Mapper 靠它选实例与表名")
    void should_bindTargetContext_when_visitingEachShard() {
        final Set<String> seen = new LinkedHashSet<String>();

        fanout.readAcrossTenants("alert_event", "2026-11-01", "2026-11-30", 0, 10, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        ShardContext.Binding binding = ShardContext.current();
                        seen.add(target.getInstanceId() + "/" + target.getSuffix() + "/"
                                + binding.getResolvedInstanceId() + "/" + binding.getResolvedSuffix());
                        return Collections.emptyList();
                    }
                });

        assertThat(seen).containsExactly("ds0/_202611/ds0/_202611", "ds1/_202611/ds1/_202611");
    }

    @Test
    @DisplayName("扇出结束后绑定态必须退干净；外层已有绑定时还原外层")
    void should_restoreOuterBinding_when_fanoutEnds() {
        fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30", 0, 1, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        return Collections.singletonList(1);
                    }
                });
        assertThat(ShardContext.isBound()).isFalse();

        try (ShardContext.ShardScope outer = ShardContext.open("alert_notify_log", 903L, "2026-11-06")) {
            fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30", 0, 1, NEWEST_FIRST,
                    new java.util.function.Function<ShardTarget, List<Integer>>() {
                        @Override
                        public List<Integer> apply(ShardTarget target) {
                            return Collections.emptyList();
                        }
                    });
            // 线程池复用线程时残留内层绑定 = 下一个请求查到别人的数据，所以 close 必须还原到"打开前那一刻"
            assertThat(ShardContext.current().getLogicalTable()).isEqualTo("alert_notify_log");
        }
        assertThat(ShardContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("空分片与 null 结果都被跳过，不影响别的分片")
    void should_skipEmptyShards_when_someShardsHaveNoRows() {
        ShardQueryFanout.ShardResult<Integer> result = fanout.read("alert_event", 903L,
                "2026-09-01", "2026-11-30", 0, 5, NEWEST_FIRST,
                byMonth(Collections.<Integer>emptyList(), null, Arrays.asList(7, 6)));

        assertThat(result.getRecords()).containsExactly(7, 6);
        assertThat(result.getScannedRows()).isEqualTo(2);
        assertThat(result.isExhausted()).isTrue();
    }

    @Test
    @DisplayName("offset 超出归并结果时给空页，不抛下标越界")
    void should_returnEmptyPage_when_offsetBeyondMergedRows() {
        ShardQueryFanout.ShardResult<Integer> result = fanout.read("alert_event", 903L,
                "2026-09-01", "2026-11-30", 100, 10, NEWEST_FIRST,
                byMonth(Arrays.asList(11), Arrays.asList(9), Arrays.asList(7)));

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.isMore()).isFalse();
    }

    @Test
    @DisplayName("跨租户区间读扫所有实例：两个月 × 两桶 = 4 个分片")
    void should_scanEveryBucket_when_readingAcrossTenants() {
        final Set<String> instances = new LinkedHashSet<String>();

        ShardQueryFanout.ShardResult<Integer> result = fanout.readAcrossTenants("alert_event",
                "2026-10-01", "2026-11-30", 0, 10, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        instances.add(target.getInstanceId());
                        return Collections.singletonList(target.getInstanceIndex() + 100);
                    }
                });

        assertThat(result.getShards()).isEqualTo(4);
        assertThat(instances).containsExactly("ds0", "ds1");
        assertThat(result.getRecords()).containsExactly(101, 101, 100, 100);
    }

    @Test
    @DisplayName("计数按分片求和；截断时总数是下界，必须带 truncated 上报")
    void should_sumCountsAndFlagTruncation_when_countingAcrossShards() {
        ShardProperties capped = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
        capped.setMaxShardsPerRead(3);
        ShardQueryFanout cappedFanout = new ShardQueryFanout(ShardTestFixtures.routerOf(capped));

        ShardQueryFanout.ShardCount full = fanout.countAll("alert_event", 903L,
                "2026-09-01", "2026-11-30", new java.util.function.ToLongFunction<ShardTarget>() {
                    @Override
                    public long applyAsLong(ShardTarget target) {
                        return 5L;
                    }
                });
        assertThat(full.getTotal()).isEqualTo(15L);
        assertThat(full.getShards()).isEqualTo(3);
        assertThat(full.isTruncated()).isFalse();

        ShardQueryFanout.ShardCount truncated = cappedFanout.countAll("alert_event", 903L,
                "2026-09-01", "2026-12-31", new java.util.function.ToLongFunction<ShardTarget>() {
                    @Override
                    public long applyAsLong(ShardTarget target) {
                        return 5L;
                    }
                });
        assertThat(truncated.getTotal()).isEqualTo(15L);
        assertThat(truncated.getRequiredShards()).isEqualTo(4);
        assertThat(truncated.isTruncated()).isTrue();
    }

    @Test
    @DisplayName("跨租户计数扫全部实例")
    void should_sumEveryBucket_when_countingAcrossTenants() {
        ShardQueryFanout.ShardCount count = fanout.countAllAcrossTenants("alert_event",
                "2026-11-01", "2026-11-30", new java.util.function.ToLongFunction<ShardTarget>() {
                    @Override
                    public long applyAsLong(ShardTarget target) {
                        return 2L;
                    }
                });

        assertThat(count.getShards()).isEqualTo(2);
        assertThat(count.getTotal()).isEqualTo(4L);
    }

    @Test
    @DisplayName("分片取数抛异常时包成 BizException 并点名是哪个分片（不能让人猜库）")
    void should_wrapFetcherFailureWithTarget_when_readCappedThrows() {
        assertThatThrownBy(() -> fanout.readCapped("alert_event", 903L, "2026-11-01", "2026-11-30",
                0, 10, NEWEST_FIRST, new ShardQueryFanout.ShardRowFetcher<Integer>() {
                    @Override
                    public List<Integer> fetch(ShardTarget target, int maxRows) throws Exception {
                        throw new java.sql.SQLException("boom");
                    }
                }))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("取数失败")
                .hasMessageContaining("ds1")
                .hasMessageContaining("alert_event_202611");
    }

    @Test
    @DisplayName("分页入参非法一律拒绝：limit<1、offset<0、offset+limit 溢出 int")
    void should_rejectPagingArguments_when_theyAreInvalid() {
        java.util.function.Function<ShardTarget, List<Integer>> noop =
                new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        return Collections.emptyList();
                    }
                };

        assertThatThrownBy(() -> fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30",
                0, 0, NEWEST_FIRST, noop))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("limit 必须 ≥ 1");
        assertThatThrownBy(() -> fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30",
                -1, 10, NEWEST_FIRST, noop))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("offset 不能为负");
        assertThatThrownBy(() -> fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30",
                Integer.MAX_VALUE, 10, NEWEST_FIRST, noop))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超出 int 范围");
    }

    @Test
    @DisplayName("comparator / 取数函数为空直接拒绝：归并没有确定的序就是错的")
    void should_rejectNullCollaborators_when_comparatorOrFetcherMissing() {
        assertThatThrownBy(() -> fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30",
                0, 10, null, new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        return Collections.emptyList();
                    }
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comparator");

        assertThatThrownBy(() -> fanout.read("alert_event", 903L, "2026-11-01", "2026-11-30",
                0, 10, NEWEST_FIRST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perShardQuery");

        assertThatThrownBy(() -> fanout.countAll("alert_event", 903L, "2026-11-01", "2026-11-30", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perShardCount");
    }

    @Test
    @DisplayName("没有分片规则的表来扇出 → 拒绝（配置漏了表要当场看得见）")
    void should_reject_when_tableHasNoRule() {
        assertThatThrownBy(() -> fanout.read("sys_tenant", 903L, "2026-11-01", "2026-11-30",
                0, 10, NEWEST_FIRST, new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        return Collections.emptyList();
                    }
                }))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有分片规则");
    }

    @Test
    @DisplayName("结果与计数的 toString 只报计数与截断，不报连接串")
    void should_renderSafeToStrings_when_resultsPrinted() {
        ShardQueryFanout.ShardResult<Integer> result = fanout.read("alert_event", 903L,
                "2026-11-01", "2026-11-30", 0, 2, NEWEST_FIRST,
                new java.util.function.Function<ShardTarget, List<Integer>>() {
                    @Override
                    public List<Integer> apply(ShardTarget target) {
                        return Arrays.asList(1, 2);
                    }
                });

        assertThat(result.toString()).contains("shards=").contains("truncated=false");
        assertThat(result.toString()).doesNotContain("jdbc").doesNotContain(ShardTestFixtures.SECRET);
        assertThat(fanout.countAll("alert_event", 903L, "2026-11-01", "2026-11-30",
                new java.util.function.ToLongFunction<ShardTarget>() {
                    @Override
                    public long applyAsLong(ShardTarget target) {
                        return 1L;
                    }
                }).toString()).contains("total=1");
    }

    /** 按分片访问顺序返回各自行数的取数函数（每次调用一个参数，参数个数要等于分片数）。 */
    @SafeVarargs
    private static java.util.function.Function<ShardTarget, List<Integer>> byMonth(List<Integer>... perShard) {
        final List<Integer>[] rows = perShard;
        return new java.util.function.Function<ShardTarget, List<Integer>>() {
            private int cursor;

            @Override
            public List<Integer> apply(ShardTarget target) {
                return cursor < rows.length ? rows[cursor++] : Collections.<Integer>emptyList();
            }
        };
    }

    private static List<Integer> tenRows(int base) {
        List<Integer> rows = new ArrayList<Integer>();
        for (int i = 0; i < 10; i++) {
            rows.add(base - i);
        }
        return rows;
    }
}
