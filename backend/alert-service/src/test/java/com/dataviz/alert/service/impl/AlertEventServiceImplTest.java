package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.service.AlertEventService;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.alert.vo.AlertEventVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.mybatis.shard.ShardContext;
import com.dataviz.common.mybatis.shard.ShardMode;
import com.dataviz.common.mybatis.shard.ShardProperties;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.common.mybatis.shard.ShardRouteBy;
import com.dataviz.common.mybatis.shard.ShardRouter;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PI6：{@code alert_event} 的分片读写口径。形状抄 PI5 之后的 openapi 那一份，因为那一份是在真进程里
 * 被运行时纠正过才定型的。这里要钉住的是它踩过的三个坑，而不是"看起来能跑"：
 * <ol>
 *   <li>行查询带排序，计数<b>不带</b>排序也不带投影 —— MP 会把投影搬进 {@code COUNT(a, b)}（1064），
 *       而 {@code COUNT(...) ORDER BY} 在 {@code only_full_group_by} 下同样是死路；</li>
 *   <li>服务端补的时间窗必须同时出现在分片计划与 WHERE 里；</li>
 *   <li>截断与"窗口是补的"要能被调用方看见（{@code truncated} / {@code shards} / {@code partial}）。</li>
 * </ol>
 * 这些用例把 mapper mock 掉了，所以它们钉的是编排与 SQL 形状，<b>不是可执行性</b> —— 可执行性由
 * {@code devlog/_pi/pi6-alert-shard-read.cjs} 那条走真进程真两个实例的判据负责（API-171 的教训）。
 */
@ExtendWith(MockitoExtension.class)
class AlertEventServiceImplTest {

    @Mock
    private AlertEventMapper eventMapper;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<ShardQueryFanout> shardFanoutProvider;

    private AlertEventServiceImpl service;

    @BeforeEach
    void setUp() {
        MybatisPlusTableInfoHelper.initFor(AlertEvent.class);
        lenient().when(shardFanoutProvider.getIfAvailable()).thenReturn(null);
        service = new AlertEventServiceImpl(eventMapper, shardFanoutProvider);
        ReflectionTestUtils.setField(service, "queryMonths", 3);
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).username("admin").tenantId("1").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private static AlertEvent row(long id, LocalDateTime at) {
        return AlertEvent.builder().id(id).ruleId(7L).severity("CRITICAL").status("PENDING")
                .tenantId(1L).createTime(at).message("越界").build();
    }

    // ---------- 写侧：路由键由这一行自己说清楚 ----------

    @Test
    @DisplayName("create 在写之前就把 create_time 定下来，并用这一行的 (租户, 时间) 绑住分片作用域")
    void should_bindRowOwnShardKeys_when_creating() {
        final List<Object> seenTime = new ArrayList<Object>();
        final List<Object> seenTenant = new ArrayList<Object>();
        when(eventMapper.insert(any(AlertEvent.class))).thenAnswer(inv -> {
            ShardContext.Binding b = ShardContext.current();
            seenTime.add(b == null ? null : b.getTimeValue());
            seenTenant.add(b == null ? null : b.getTenantId());
            return 1;
        });

        AlertEvent event = AlertEvent.builder().ruleId(7L).tenantId(903L).status("PENDING").build();
        service.create(event);

        assertNotNull(event.getCreateTime(), "路由键必须在 insert 之前就有值，不能等 FieldFill 事后补");
        assertEquals(1, seenTime.size());
        assertEquals(event.getCreateTime(), seenTime.get(0), "绑定的时间必须就是落库那一行的时间");
        assertEquals(903L, seenTenant.get(0), "绑定的租户必须来自这一行本身（调度线程没有会话）");
        assertFalse(ShardContext.isBound(), "作用域必须随 create 一起关闭");
    }

    @Test
    @DisplayName("update 缺少分表键时当场拒绝，而不是凭裸 id 猜一张月表")
    void should_refuseUpdate_when_shardKeyMissing() {
        AlertEvent event = AlertEvent.builder().id(5L).tenantId(1L).build();

        BizException e = assertThrows(BizException.class, () -> service.update(event));

        assertTrue(e.getMessage().contains("create_time"), e.getMessage());
        verify(eventMapper, never()).updateById(any(AlertEvent.class));
    }

    // ---------- 读侧：单表路径与今天一致 ----------

    @Test
    @DisplayName("分片关着：一次单表分页，排序带 id 兜底，三个分片字段全为 null")
    void should_useSingleTablePath_when_shardingDisabled() {
        Page<AlertEvent> page = new Page<AlertEvent>(1, 10);
        page.setRecords(Collections.singletonList(row(3L, LocalDateTime.of(2026, 11, 6, 8, 0))));
        page.setTotal(37L);
        when(eventMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<AlertEventVO> result = service.page(7L, "PENDING", null, 1, 10);

        assertEquals(37L, result.getTotal());
        assertNull(result.getTruncated());
        assertNull(result.getShards());
        assertNull(result.getWindowDefaulted());

        LambdaQueryWrapper<AlertEvent> w = captureRowWrapper();
        // 列名要从渲染出来的 SQL 段读，不能对 getOrderBy() 的 toString 做子串匹配 ——
        // 那里面装的是 lambda 片段对象，打出来是类名+哈希（PI6 第一次跑就是这么误红的）
        String sql = w.getSqlSegment().toLowerCase();
        assertTrue(sql.contains("order by") && sql.contains("create_time") && sql.contains("id"),
                "同秒事件很常见，排序少了 id 兜底就会翻页重行/漏行: " + sql);
        assertTrue(isBlank(w.getSqlSelect()), "alert_event 没有需要屏蔽的列，不该带白名单投影");
    }

    // ---------- 读侧：扇出 ----------

    @Test
    @DisplayName("开着分片：窗口内逐张月表取前 offset+limit 行，并把窗口边界写进 WHERE")
    void should_fanOutAndBoundWindow_when_shardingEnabled() {
        when(shardFanoutProvider.getIfAvailable()).thenReturn(fanout(36));
        when(eventMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<AlertEvent> p = new Page<AlertEvent>(1, 10);
            p.setRecords(Collections.<AlertEvent>emptyList());
            return p;
        });
        when(eventMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        PageResult<AlertEventVO> result = service.page(7L, "PENDING", null, 1, 10);

        // 3 个月滚动窗口跨 4 张月表：起点落在那个月的中间，那个月照样要扫
        assertEquals(Integer.valueOf(4), result.getShards());
        assertEquals(Boolean.FALSE, result.getTruncated());
        assertEquals(Boolean.TRUE, result.getWindowDefaulted());

        LambdaQueryWrapper<AlertEvent> count = captureLastCountWrapper();
        String sql = count.getSqlSegment();
        assertTrue(sql.contains("create_time >=") && sql.contains("create_time <="),
                "窗口只用来选分片是不够的：最早那张月表装的是整月的行，不画边界 total 就会虚高: " + sql);
        assertTrue(count.getParamNameValuePairs().size() >= 4,
                "边界必须是参数而不是拼进字符串: " + count.getParamNameValuePairs());
    }

    @Test
    @DisplayName("计数的 wrapper 既不带列投影也不带 ORDER BY —— PI5 那两个 1064/1055 的形状一次性避开")
    void should_keepCountWrapperPlain_when_fanOutCounting() {
        when(shardFanoutProvider.getIfAvailable()).thenReturn(fanout(36));
        when(eventMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        AlertEventService.EventCounts counts = service.counts();

        LambdaQueryWrapper<AlertEvent> count = captureLastCountWrapper();
        assertTrue(isBlank(count.getSqlSelect()),
                "计数带投影会被 MP 拼成 COUNT(a, b) → MySQL 1064: " + count.getSqlSelect());
        assertTrue(count.getExpression().getOrderBy().isEmpty(),
                "计数带 ORDER BY 在 only_full_group_by 下同样是死路");
        assertTrue(counts.isPartial(), "窗口是服务端补的，这四个数不是全历史，必须标出来");
    }

    @Test
    @DisplayName("分片关闭时计数是全历史：partial 必须是 false，SQL 里也不该有时间窗")
    void should_reportFullHistoryCounts_when_shardingDisabled() {
        when(eventMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        AlertEventService.EventCounts counts = service.counts();

        assertFalse(counts.isPartial());
        assertEquals(5L, counts.getPending());
        LambdaQueryWrapper<AlertEvent> count = captureLastCountWrapper();
        assertFalse(count.getSqlSegment().contains("create_time"),
                "单表路径不该被塞进时间窗: " + count.getSqlSegment());
    }

    @Test
    @DisplayName("抑制判定被分片上限截断时按\"已有未处理事件\"处理 —— 宁可少发一条，不能刷屏")
    void should_suppress_when_fanOutCountTruncated() {
        when(shardFanoutProvider.getIfAvailable()).thenReturn(fanout(2));
        when(eventMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertTrue(service.hasOpenEvent(7L, 1L));
    }

    // ---------- 按 id 定位（ack/resolve 的前半段） ----------

    @Test
    @DisplayName("分片模式下按 id 定位是窗口内有界扫描；找不到就点名窗口，不静默跳过状态流转")
    void should_scanWindowAndRefuseSilently_when_locatingById() {
        when(shardFanoutProvider.getIfAvailable()).thenReturn(fanout(36));
        when(eventMapper.selectList(any(Wrapper.class))).thenReturn(Collections.<AlertEvent>emptyList());

        BizException e = assertThrows(BizException.class, () -> service.markStatus(99L, "ACKNOWLEDGED", false));

        assertTrue(e.getMessage().contains("窗口") || e.getMessage().contains("个月"), e.getMessage());
        verify(eventMapper, never()).updateById(any(AlertEvent.class));
    }

    @Test
    @DisplayName("分片模式下定位到行之后，回写用的是这一行自己的租户与时间")
    void should_updateLocatedRowWithItsOwnKeys_when_shardingEnabled() {
        when(shardFanoutProvider.getIfAvailable()).thenReturn(fanout(36));
        when(eventMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row(42L, LocalDateTime.of(2026, 10, 3, 8, 0))));
        final List<Object> boundTimes = new ArrayList<Object>();
        when(eventMapper.updateById(any(AlertEvent.class))).thenAnswer(inv -> {
            ShardContext.Binding b = ShardContext.current();
            boundTimes.add(b == null ? null : b.getTimeValue());
            return 1;
        });

        service.markStatus(42L, "RESOLVED", true);

        assertEquals(1, boundTimes.size());
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 0), boundTimes.get(0), "回写必须路由到这一行所在的那张月表");
    }

    // ---------- helpers ----------

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<AlertEvent> captureRowWrapper() {
        ArgumentCaptor<Wrapper<AlertEvent>> cap =
                (ArgumentCaptor<Wrapper<AlertEvent>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper).selectPage(any(Page.class), cap.capture());
        return (LambdaQueryWrapper<AlertEvent>) cap.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<AlertEvent> captureLastCountWrapper() {
        ArgumentCaptor<Wrapper<AlertEvent>> cap =
                (ArgumentCaptor<Wrapper<AlertEvent>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper, atLeastOnce()).selectCount(cap.capture());
        List<Wrapper<AlertEvent>> all = cap.getAllValues();
        return (LambdaQueryWrapper<AlertEvent>) all.get(all.size() - 1);
    }

    /** 与生产同构的分片配置（实例 url 是假的：Hikari 懒初始化，这些测试不连库）。 */
    private static ShardQueryFanout fanout(int maxShardsPerRead) {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setMaxShardsPerRead(maxShardsPerRead);
        ShardProperties.Instance ds0 = new ShardProperties.Instance();
        ds0.setId("ds0");
        ds0.setUrl("jdbc:mysql://127.0.0.1:3306/db_alert");
        ds0.setUsername("root");
        ds0.setPassword("unused-in-tests");
        ShardProperties.Instance ds1 = new ShardProperties.Instance();
        ds1.setId("ds1");
        ds1.setUrl("jdbc:mysql://127.0.0.1:3307/db_alert");
        ds1.setUsername("root");
        ds1.setPassword("unused-in-tests");
        properties.setInstances(Arrays.asList(ds0, ds1));
        ShardProperties.Rule rule = new ShardProperties.Rule();
        rule.setTable(AlertEventShardReader.TABLE);
        rule.setMode(ShardMode.MONTH);
        // 与生产一致：这两张表按租户哈希分库，所以一次带租户的读只打一个实例
        rule.setRouteBy(ShardRouteBy.TENANT);
        rule.setBuckets(2);
        properties.setRules(Collections.singletonList(rule));
        properties.validate();
        return new ShardQueryFanout(new ShardRouter(properties));
    }
}
