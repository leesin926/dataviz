package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.alert.engine.AlertEvaluator;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertEventService;
import com.dataviz.alert.service.AlertNotifyService;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.redis.util.CacheHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 阶段 BE1（API-111）的替代判据：假引擎退场后，<b>自动巡检只剩这一个调度器，而且它默认不开</b>。
 * 这里钉的是"开了之后它到底怎么判"，免得下一次把开关一翻就变成另一套没人验证过的行为。
 *
 * <p>PI6 起 {@code alert_event} 的每一条 SQL 都从 {@link AlertEventService} 出去（分片模式下每处都要带
 * 路由键，收到一个服务里才 review 得住），所以这里 mock 的是那个服务而不是 mapper。巡检逻辑与
 * "这一行落哪张月表"因此解耦；交给下游的两件事本文件各钉一条：事件自带 tenantId + createTime，
 * 抑制判定按<b>规则的</b>租户问（调度线程没有会话）。
 */
class AlertCheckServiceImplTest {

    private AlertRuleMapper alertRuleMapper;
    private AlertEventService alertEventService;
    private AlertNotifyService alertNotifyService;
    private AlertEvaluator alertEvaluator;
    private CacheHelper cacheHelper;
    private AlertCheckServiceImpl checkService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(AlertRule.class, AlertEvent.class);
    }

    @BeforeEach
    void setUp() {
        alertRuleMapper = mock(AlertRuleMapper.class);
        alertEventService = mock(AlertEventService.class);
        alertNotifyService = mock(AlertNotifyService.class);
        alertEvaluator = mock(AlertEvaluator.class);
        cacheHelper = mock(CacheHelper.class);
        checkService = new AlertCheckServiceImpl(alertRuleMapper, alertEventService,
                alertNotifyService, alertEvaluator, cacheHelper);
        // create() 就是"写进去并拿到自增 id"那一步；这里按 mapper 的语义模拟成"回填 id 后原样返回"
        when(alertEventService.create(any(AlertEvent.class))).thenAnswer(inv -> {
            AlertEvent e = inv.getArgument(0);
            e.setId(501L);
            return e;
        });
    }

    private void enable() {
        ReflectionTestUtils.setField(checkService, "checkEnabled", true);
    }

    private AlertRule rule(Long id, Integer duration) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName("大区销售额跌破 1000");
        rule.setTenantId(1L);
        rule.setSeverity("CRITICAL");
        rule.setCondition("LT");
        rule.setThreshold(new BigDecimal("1000"));
        rule.setEnabled(true);
        rule.setDuration(duration);
        return rule;
    }

    @SuppressWarnings("unchecked")
    private void stubRules(AlertRule... rules) {
        when(alertRuleMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(rules));
    }

    @Test
    @DisplayName("定时巡检 - 总开关关闭时一条规则都不查")
    void should_touchNothing_when_checkDisabled() {
        // Given：默认构造出来的实例没被 enable()，等价于 alert.check.enabled:false
        // When
        checkService.checkAllRules();
        // Then
        verifyNoInteractions(alertRuleMapper, alertEvaluator, alertEventService, alertNotifyService, cacheHelper);
    }

    @Test
    void should_createEventAndNotify_when_thresholdBreachedWithoutDuration() {
        // Given
        enable();
        AlertRule r = rule(7L, null);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("820"), true, null));

        // When
        checkService.checkAllRules();

        // Then：事件字段取自规则本身，消息带当前值与阈值，状态是待处理
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventService).create(captor.capture());
        AlertEvent event = captor.getValue();
        assertEquals(7L, event.getRuleId());
        assertEquals(501L, event.getId());
        assertEquals("CRITICAL", event.getSeverity());
        assertEquals("PENDING", event.getStatus());
        assertEquals(1L, event.getTenantId());
        assertEquals(new BigDecimal("820"), event.getTriggerValue());
        assertTrue(event.getMessage().contains("大区销售额跌破 1000"), event.getMessage());
        assertTrue(event.getMessage().contains("820"), event.getMessage());
        assertTrue(event.getMessage().contains("1000"), event.getMessage());
        // 分表键必须在交给写入口之前就有值：createTime 平时由 FieldFill.INSERT 在 mapper 内部才填，
        // 而路由要的是"这一行落哪个月"，晚一步填就是按 A 月路由、写进 B 月
        assertNotNull(event.getCreateTime(), "事件必须带着 create_time 交给写入口，否则分片路由无键可用");
        verify(alertNotifyService).notify(event);
        // duration 为空即"立即"，不该去写越界起始时刻
        verify(cacheHelper, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void should_suppressEvent_when_durationNotReachedYet() {
        // Given：持续 120s，本轮第一次越界
        enable();
        AlertRule r = rule(8L, 120);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("900"), true, null));
        when(cacheHelper.get("alert:breach:8")).thenReturn(null);

        // When
        checkService.checkAllRules();

        // Then：只登记首次越界时刻，不生成事件、不发通知
        verify(cacheHelper).set(eq("alert:breach:8"), any(), eq(600L), eq(TimeUnit.SECONDS));
        verify(alertEventService, never()).create(any(AlertEvent.class));
        verifyNoInteractions(alertNotifyService);
    }

    @Test
    void should_fireAfterSustained_when_breachMarkerOlderThanDuration() {
        // Given：首次越界在 200s 前，duration=120
        enable();
        AlertRule r = rule(9L, 120);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("1"), true, null));
        when(cacheHelper.get("alert:breach:9")).thenReturn(System.currentTimeMillis() - 200_000L);

        // When
        checkService.checkAllRules();

        // Then
        verify(alertEventService).create(any(AlertEvent.class));
        verify(alertNotifyService).notify(any(AlertEvent.class));
    }

    @Test
    void should_notFire_when_sameRuleStillHasPendingEvent() {
        // Given：越界且时长够，但已有一条 PENDING 没被认领
        enable();
        AlertRule r = rule(10L, 0);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("1"), true, null));
        when(alertEventService.hasOpenEvent(10L, 1L)).thenReturn(true);

        // When
        checkService.checkAllRules();

        // Then：抑制重复告警，不刷第二条
        verify(alertEventService, never()).create(any(AlertEvent.class));
        verifyNoInteractions(alertNotifyService);
    }

    /**
     * 抑制判定必须按<b>规则的租户</b>问，不能自己去看会话：巡检跑在调度线程上，那里没有租户上下文，
     * 而分片模式下"没有租户"不是"扫默认库"、是当场拒绝 —— 那一拒绝会让抑制永远失效，
     * 一条没人认领的告警就每分钟刷一条。
     */
    @Test
    @DisplayName("重复告警抑制按规则的租户问，不依赖会话（调度线程没有租户）")
    void should_askSuppressionWithRuleTenant_when_scheduledThreadHasNoSession() {
        // Given
        enable();
        AlertRule r = rule(21L, 0);
        r.setTenantId(903L);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("1"), true, null));
        when(alertEventService.hasOpenEvent(21L, 903L)).thenReturn(true);

        // When
        checkService.checkAllRules();

        // Then
        verify(alertEventService).hasOpenEvent(21L, 903L);
        verify(alertEventService, never()).create(any(AlertEvent.class));
    }

    @Test
    void should_clearBreachMarker_when_valueBackWithinThreshold() {
        // Given：上一轮记过越界时刻，本轮回到阈值内
        enable();
        AlertRule r = rule(11L, 60);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("1500"), false, null));

        // When
        checkService.checkAllRules();

        // Then：计时重新开始，否则下次真越界会拿旧的起点立刻满足 duration
        verify(cacheHelper).delete("alert:breach:11");
        verify(alertEventService, never()).create(any(AlertEvent.class));
    }

    @Test
    void should_leaveMarkerUntouched_when_queryReturnedNoRow() {
        // Given：取数成功但无结果行 —— 这是"无法判定"，不是"已恢复"
        enable();
        AlertRule r = rule(12L, 60);
        stubRules(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(null, false, "指标查询无结果"));

        // When
        checkService.checkAllRules();

        // Then：不生成事件；本轮不清计时（一次空结果不该抹掉已经累计的越界时长）
        verify(alertEventService, never()).create(any(AlertEvent.class));
        verify(cacheHelper, never()).delete(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_keepGoing_when_oneRuleThrows() {
        // Given：第一条抛错（数据源挂了 / SQL 写错），第二条正常
        enable();
        AlertRule bad = rule(13L, 0);
        AlertRule good = rule(14L, 0);
        stubRules(bad, good);
        when(alertEvaluator.evaluate(bad)).thenThrow(new IllegalStateException("数据源不可达"));
        when(alertEvaluator.evaluate(good)).thenReturn(new AlertEvaluator.Result(new BigDecimal("1"), true, null));

        // When：不向外抛
        checkService.checkAllRules();

        // Then：坏的那条也被评估过（异常来自评估器），一条规则失败不带走其余
        verify(alertEvaluator).evaluate(bad);
        verify(alertEvaluator).evaluate(good);
        verify(alertEventService).create(any(AlertEvent.class));
    }

    @Test
    void should_notEvaluate_when_manualCheckTargetsDisabledRule() {
        // Given
        AlertRule r = rule(15L, 0);
        r.setEnabled(false);
        when(alertRuleMapper.selectById(15L)).thenReturn(r);

        // When
        checkService.checkRule(15L);

        // Then
        verifyNoInteractions(alertEvaluator, alertNotifyService);
        verify(alertEventService, never()).create(any(AlertEvent.class));
    }

    @Test
    void should_returnQuietly_when_manualCheckTargetsMissingRule() {
        // Given
        when(alertRuleMapper.selectById(16L)).thenReturn(null);

        // When
        checkService.checkRule(16L);

        // Then
        verifyNoInteractions(alertEvaluator, alertNotifyService);
        verify(alertEventService, never()).create(any(AlertEvent.class));
    }

    @Test
    void should_evaluateOnce_when_manualCheckGivenEnabledRule() {
        // Given：手动巡检不该受总开关影响（否则"立即试算"要点开配置才能用）
        AlertRule r = rule(17L, 0);
        when(alertRuleMapper.selectById(17L)).thenReturn(r);
        when(alertEvaluator.evaluate(r)).thenReturn(new AlertEvaluator.Result(new BigDecimal("2"), true, null));

        // When
        checkService.checkRule(17L);

        // Then
        verify(alertEvaluator).evaluate(r);
        verify(alertNotifyService).notify(any(AlertEvent.class));
        verify(alertRuleMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_queryEnabledRulesOnly_when_scheduled() {
        // Given：把 selectList 的入参抓出来，证明它按 enabled=true 过滤而不是全表
        enable();
        when(alertRuleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.<AlertRule>emptyList());

        // When
        checkService.checkAllRules();

        // Then
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(alertRuleMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.toLowerCase().contains("enabled"), sqlSegment);
        verifyNoInteractions(alertEventService, alertNotifyService);
    }
}
