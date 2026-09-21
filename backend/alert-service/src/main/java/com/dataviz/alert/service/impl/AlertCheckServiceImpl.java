package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.engine.AlertEvaluator;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertCheckService;
import com.dataviz.alert.service.AlertNotifyService;
import com.dataviz.common.redis.util.CacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 规则评估与事件生成。
 * <p>
 * 三件事决定了它能不能真跑：①取数走 datasource-service 的内部只读查询；②{@code duration} 用 Redis
 * 记住"首次越界时刻"，连续越界够久才报（每 60s 一轮，所以 duration 的分辨率就是 60s）；
 * ③同一规则还有未处理（PENDING）事件时抑制重复告警，否则一条没人认领的告警会每分钟刷一条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertCheckServiceImpl implements AlertCheckService {

    /** 首次越界时刻的缓存键前缀 */
    private static final String BREACH_KEY_PREFIX = "alert:breach:";

    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifyService alertNotifyService;
    private final AlertEvaluator alertEvaluator;
    private final CacheHelper cacheHelper;

    /**
     * 真取数之后这就不再是空转，所以给一个总开关：默认关，避免在开发机上每分钟对全部数据源
     * 发一轮查询，也避免误配的通知渠道把消息真发出去。
     */
    @Value("${alert.check.enabled:false}")
    private boolean checkEnabled;

    @Override
    @Scheduled(fixedDelay = 60000)
    public void checkAllRules() {
        if (!checkEnabled) {
            return;
        }
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getEnabled, true);
        List<AlertRule> rules = alertRuleMapper.selectList(wrapper);
        for (AlertRule rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                // 单条规则失败（数据源挂了、SQL 写错）不影响其余规则，但必须留痕
                log.error("告警规则评估失败: id={}, name={}, {}", rule.getId(), rule.getName(), e.getMessage());
            }
        }
    }

    @Override
    public void checkRule(Long ruleId) {
        AlertRule rule = alertRuleMapper.selectById(ruleId);
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            return;
        }
        checkRule(rule);
    }

    private void checkRule(AlertRule rule) {
        AlertEvaluator.Result result = alertEvaluator.evaluate(rule);

        if (result.getValue() == null || !result.isTriggered()) {
            // 越界状态被打断，计时重新开始
            cacheHelper.delete(breachKey(rule.getId()));
            return;
        }

        if (!sustainedLongEnough(rule)) {
            log.info("规则 {} 已越界但未达持续时长 {}s，暂不告警（当前值 {}）",
                    rule.getId(), rule.getDuration(), result.getValue());
            return;
        }

        if (hasPendingEvent(rule.getId())) {
            log.info("规则 {} 仍有未处理事件，抑制重复告警", rule.getId());
            return;
        }

        createAlertEvent(rule, result.getValue());
    }

    /**
     * @return duration 为空或 0 时立即为真；否则要求首次越界到现在已连续越界够久
     */
    private boolean sustainedLongEnough(AlertRule rule) {
        Integer durationSeconds = rule.getDuration();
        if (durationSeconds == null || durationSeconds <= 0) {
            return true;
        }
        String key = breachKey(rule.getId());
        Object first = cacheHelper.get(key);
        long now = System.currentTimeMillis();
        if (first == null) {
            // TTL 取 2 倍持续时长且不低于 10 分钟：既要在中途被打断前一直活着，
            // 也不能因为某轮服务没跑就留下一个几小时前的"首次越界"
            cacheHelper.set(key, now, Math.max(durationSeconds * 2L, 600L), TimeUnit.SECONDS);
            return false;
        }
        long firstAt = first instanceof Number ? ((Number) first).longValue() : now;
        return now - firstAt >= durationSeconds * 1000L;
    }

    private boolean hasPendingEvent(Long ruleId) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getRuleId, ruleId).eq(AlertEvent::getStatus, "PENDING");
        return alertEventMapper.selectCount(wrapper) > 0;
    }

    private String breachKey(Long ruleId) {
        return BREACH_KEY_PREFIX + ruleId;
    }

    @Transactional
    protected void createAlertEvent(AlertRule rule, BigDecimal triggerValue) {
        AlertEvent event = AlertEvent.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .triggerValue(triggerValue)
                .severity(rule.getSeverity())
                .status("PENDING")
                .message(String.format("规则「%s」触发: 当前值 %s，条件 %s 阈值 %s",
                        rule.getName(), triggerValue, rule.getCondition(), rule.getThreshold()))
                .tenantId(rule.getTenantId())
                .createTime(LocalDateTime.now())
                .build();
        alertEventMapper.insert(event);
        log.info("生成告警事件: id={}, ruleId={}, severity={}, value={}",
                event.getId(), rule.getId(), rule.getSeverity(), triggerValue);

        alertNotifyService.notify(event);
    }
}
