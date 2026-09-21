package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.AlertRuleCreateDTO;
import com.dataviz.alert.dto.AlertRuleUpdateDTO;
import com.dataviz.alert.engine.AlertEvaluator;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertRuleService;
import com.dataviz.alert.vo.AlertRuleTestVO;
import com.dataviz.alert.vo.AlertRuleVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;
    private final ObjectMapper objectMapper;
    private final AlertEvaluator alertEvaluator;

    @Override
    @Transactional
    public Long create(AlertRuleCreateDTO dto) {
        AlertRule rule = new AlertRule();
        BeanUtils.copyProperties(dto, rule);
        rule.setNotifyChannels(toJson(dto.getNotifyChannels()));
        rule.setEnabled(true);
        alertRuleMapper.insert(rule);
        log.info("Created alert rule: id={}, name={}", rule.getId(), rule.getName());
        return rule.getId();
    }

    @Override
    @Transactional
    public void update(AlertRuleUpdateDTO dto) {
        AlertRule rule = alertRuleMapper.selectById(dto.getId());
        if (rule == null) {
            throw new BizException("Alert rule not found: " + dto.getId());
        }
        BeanUtils.copyProperties(dto, rule);
        if (dto.getNotifyChannels() != null) {
            rule.setNotifyChannels(toJson(dto.getNotifyChannels()));
        }
        alertRuleMapper.updateById(rule);
        log.info("Updated alert rule: id={}", rule.getId());
    }

    @Override
    public AlertRuleVO getById(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("Alert rule not found: " + id);
        }
        return toVO(rule);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        alertRuleMapper.deleteById(id);
        log.info("Deleted alert rule: id={}", id);
    }

    @Override
    public PageResult<AlertRuleVO> page(PageQuery pageQuery, String keyword, String type, String severity) {
        Page<AlertRule> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AlertRule::getName, keyword);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(AlertRule::getType, type);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertRule::getSeverity, severity);
        }
        wrapper.orderByDesc(AlertRule::getCreateTime);
        Page<AlertRule> result = alertRuleMapper.selectPage(page, wrapper);
        List<AlertRuleVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    @Transactional
    public void enable(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("Alert rule not found: " + id);
        }
        rule.setEnabled(true);
        alertRuleMapper.updateById(rule);
        log.info("Enabled alert rule: id={}", id);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("Alert rule not found: " + id);
        }
        rule.setEnabled(false);
        alertRuleMapper.updateById(rule);
        log.info("Disabled alert rule: id={}", id);
    }

    @Override
    public AlertRuleTestVO testRule(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("Alert rule not found: " + id);
        }
        // 试跑只走到"取数 + 判定"为止：不建事件、不派发通知，避免测试一次就骚扰一遍渠道
        AlertEvaluator.Result result = alertEvaluator.evaluate(rule);
        Integer duration = rule.getDuration();
        String note = result.getNote();
        if (note == null && duration != null && duration > 0) {
            note = "试跑不评估持续时长（规则配置 " + duration + "s），真实告警需持续越界该时长";
        }
        log.info("Tested alert rule: id={}, name={}, value={}, triggered={}",
                id, rule.getName(), result.getValue(), result.isTriggered());
        return AlertRuleTestVO.builder()
                .value(result.getValue())
                .triggered(result.isTriggered())
                .note(note)
                .build();
    }

    private AlertRuleVO toVO(AlertRule rule) {
        AlertRuleVO vo = new AlertRuleVO();
        BeanUtils.copyProperties(rule, vo);
        vo.setNotifyChannels(fromJson(rule.getNotifyChannels()));
        return vo;
    }

    private String toJson(List<String> list) {
        if (list == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notify channels", e);
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize notify channels: {}", json, e);
            return Collections.emptyList();
        }
    }
}
