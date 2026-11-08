package com.dataviz.alert.engine;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertNotifyLog;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertNotifyLogMapper;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.mybatis.shard.ShardContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code alert_notify_log} 唯一的写入点（PI4 把这张表接进分片时它一个测试都没有，
 * 于是"派发了几条、失败几条"这件事在这张表上是零覆盖）。
 *
 * <p>钉两件与分片直接有关的事：
 * ① 流水的租户<b>跟着事件走</b> —— 这张表在租户拦截器黑名单里，没人替它注入，也没人替它填；
 *   打开分片后 {@code route-by=TENANT} 的路由键就是这个值，留空等于当场拒绝写入。
 * ② 插入那一刻线程上必须绑着 {@code (alert_notify_log, 租户, 这一行的 create_time)}。
 * 失败派发也要记账（BH3 的口径），所以失败路径同样过一遍这两条。
 */
class NotifyDispatcherTest {

    private NotifyChannelMapper channelMapper;
    private AlertNotifyLogMapper logMapper;
    private AlertNotifyTargetService targetService;
    private AlertNotifier notifier;
    private NotifyDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        MybatisPlusTableInfoHelper.initFor(AlertNotifyLog.class, NotifyChannel.class);
        channelMapper = mock(NotifyChannelMapper.class);
        logMapper = mock(AlertNotifyLogMapper.class);
        targetService = mock(AlertNotifyTargetService.class);
        notifier = mock(AlertNotifier.class);
        when(notifier.channel()).thenReturn("WEBHOOK");
        when(channelMapper.selectOne(any(Wrapper.class))).thenReturn(new NotifyChannel());
        when(targetService.resolveTargets(any(AlertRule.class), any(NotifyChannel.class)))
                .thenReturn(NotifyTargets.none());
        when(notifier.recipientOf(any(NotifyChannel.class), any(NotifyTargets.class))).thenReturn("https://hook/ck");
        dispatcher = new NotifyDispatcher(Collections.singletonList(notifier), channelMapper, logMapper,
                new ObjectMapper(), targetService);
    }

    @AfterEach
    void tearDown() {
        // 绑定残留会让同线程后续用例读到上一行的分片键（表象是"偶发查到别人的数据"）
        ShardContext.clear();
        assertFalse(ShardContext.isBound());
    }

    private static AlertRule rule() {
        AlertRule rule = new AlertRule();
        rule.setId(7L);
        rule.setName("大区销售额跌破 1000");
        rule.setTenantId(903L);
        rule.setNotifyChannels("[\"WEBHOOK\"]");
        return rule;
    }

    private static AlertEvent event(Long tenantId) {
        return AlertEvent.builder().id(501L).ruleId(7L).tenantId(tenantId)
                .status("PENDING").createTime(LocalDateTime.of(2026, 11, 6, 8, 0)).build();
    }

    @Test
    @DisplayName("成功派发：流水带上事件的租户，并且插入那一刻绑着这张表的分片键")
    void should_stampTenantAndShardScope_when_dispatchSucceeds() {
        dispatcher.dispatch(rule(), event(903L));

        final ArgumentCaptor<AlertNotifyLog> captor = ArgumentCaptor.forClass(AlertNotifyLog.class);
        verify(logMapper).insert(captor.capture());
        AlertNotifyLog record = captor.getValue();

        assertEquals("SUCCESS", record.getStatus());
        assertEquals(501L, record.getEventId());
        assertEquals(903L, record.getTenantId(),
                "这张表在租户拦截器黑名单里：不自己填就永远是 NULL，而 NULL 在 route-by=TENANT 下不是默认库，是拒绝");
        assertNotNull(record.getCreateTime());
    }

    @Test
    @DisplayName("失败派发照样记账，租户也照样带上（拒绝不能只留在日志里）")
    void should_stillLogWithTenant_when_notifierThrows() {
        doThrow(new IllegalStateException("渠道不可达")).when(notifier)
                .send(any(NotifyChannel.class), any(AlertRule.class), any(AlertEvent.class), any(NotifyTargets.class));

        dispatcher.dispatch(rule(), event(903L));

        ArgumentCaptor<AlertNotifyLog> captor = ArgumentCaptor.forClass(AlertNotifyLog.class);
        verify(logMapper).insert(captor.capture());
        AlertNotifyLog record = captor.getValue();

        assertEquals("FAILED", record.getStatus());
        assertEquals(903L, record.getTenantId());
        assertEquals("渠道不可达", record.getErrorMessage());
    }

    /**
     * 绑定必须在 insert 那一刻还活着，所以断言写在 mapper 的 Answer 里 ——
     * 等 dispatch() 返回再读就只剩"曾经绑过"，证不了作用域覆盖到写入。
     * 钉的是"路由键 == 这一行自己要写进去的 create_time"（流水行的时间是派发时刻，不是事件时间；
     * 我第一次把这里写成"等于事件的 create_time"，是判据错、不是代码错 —— 那条错判据恰好把
     * "落 A 月路由、写 B 月"与"落自己那个月"两种情况混为一谈，所以必须钉成行自己的值）。
     */
    @Test
    @DisplayName("绑定的分片键就是这一行自己的 (租户, create_time)，不是会话、也不是第二次 now()")
    void should_bindThisRowsOwnKeys_when_inserting() {
        when(logMapper.insert(any(AlertNotifyLog.class))).thenAnswer(inv -> {
            AlertNotifyLog record = inv.getArgument(0);
            ShardContext.Binding b = ShardContext.current();
            assertNotNull(b, "写 alert_notify_log 之前必须绑好分片作用域");
            assertEquals("alert_notify_log", b.getLogicalTable());
            assertEquals(903L, b.getTenantId());
            assertEquals(record.getCreateTime(), b.getTimeValue(),
                    "路由键要用这一行自己的时间；另取一次 now() 就可能在两个月表边界上按 A 月路由、写进 B 月");
            return 1;
        });

        dispatcher.dispatch(rule(), event(903L));

        assertFalse(ShardContext.isBound(), "作用域必须随派发一起关闭");
    }
}
