package com.dataviz.alert.service.impl;

import com.dataviz.alert.engine.NotifyDispatcher;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知入口。真正的派发与记账在 {@link NotifyDispatcher}（它同时写 alert_notify_log），
 * 这里只负责补上规则、回写 notifiedAt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotifyServiceImpl implements AlertNotifyService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final NotifyDispatcher notifyDispatcher;

    @Override
    public void notify(AlertEvent event) {
        AlertRule rule = alertRuleMapper.selectById(event.getRuleId());
        if (rule == null) {
            log.warn("告警事件对应的规则已不存在: eventId={}, ruleId={}", event.getId(), event.getRuleId());
            return;
        }

        notifyDispatcher.dispatch(rule, event);

        // 只有真的派发过才标记：原来 setNotifiedAt 后没有 update，事件永远停在"未通知"
        event.setNotifiedAt(LocalDateTime.now());
        alertEventMapper.updateById(event);
    }
}
