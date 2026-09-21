package com.dataviz.alert.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertCheckEngine {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final NotifyDispatcher notifyDispatcher;

    @Scheduled(fixedDelay = 60000)
    public void checkAlerts() {
        log.debug("Starting alert check cycle");
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getEnabled, true);
        List<AlertRule> activeRules = alertRuleMapper.selectList(wrapper);

        for (AlertRule rule : activeRules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Error evaluating alert rule: id={}", rule.getId(), e);
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        log.debug("Evaluating alert rule: id={}, name={}", rule.getId(), rule.getName());
        // Simulate condition evaluation - in production this would query actual data source
        BigDecimal currentValue = fetchCurrentValue(rule);
        BigDecimal threshold = extractThreshold(rule);

        if (currentValue.compareTo(threshold) > 0) {
            log.warn("Alert triggered for rule: id={}, value={}, threshold={}", 
                    rule.getId(), currentValue, threshold);
            AlertEvent event = AlertEvent.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .tenantId(rule.getTenantId())
                    .severity(rule.getSeverity())
                    .message("Alert: " + rule.getName() + " - Value " + currentValue + " exceeded threshold " + threshold)
                    .status("PENDING")
                    .triggerValue(currentValue)
                    .createTime(LocalDateTime.now())
                    .build();
            alertEventMapper.insert(event);
            notifyDispatcher.dispatch(rule, event);
        }
    }

    private BigDecimal fetchCurrentValue(AlertRule rule) {
        // In production: query the actual data source based on rule.getTargetType() and rule.getTargetId()
        return BigDecimal.valueOf(Math.random() * 100);
    }

    private BigDecimal extractThreshold(AlertRule rule) {
        // In production: parse rule.getConditionJson() to get threshold
        return BigDecimal.valueOf(80.0);
    }
}
