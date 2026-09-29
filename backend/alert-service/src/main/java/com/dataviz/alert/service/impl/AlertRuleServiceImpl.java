package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.AlertRuleCreateDTO;
import com.dataviz.alert.dto.AlertRuleUpdateDTO;
import com.dataviz.alert.engine.AlertEvaluator;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertNotifyGroupService;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.alert.service.AlertRuleService;
import com.dataviz.alert.vo.AlertRuleTestVO;
import com.dataviz.alert.vo.AlertRuleVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 告警规则的读写。写这一层时有三件事必须在保存当场做掉，控制器直接 mapper 时全都缺位：
 * <ol>
 *   <li><b>可空列要真能清空</b>：{@code description}/{@code metric_expression}/{@code threshold}/
 *       {@code duration} 这些列界面上删空就是删空，而 MyBatis-Plus 默认更新策略是 NOT_NULL，
 *       实体里的 null 根本不进 SET ⇒ "点了保存、库里纹丝不动"。所以可空列走 wrapper 显式 SET，
 *       并且<b>不留在实体上</b>（同一列出现在两处 MySQL 会报"列指定了两次"）。</li>
 *   <li><b>通知组挂在规则侧</b>：收件人该由这条规则决定，而不是由用哪个出口决定，
 *       所以引用关系跟着规则整批替换，见 {@link AlertNotifyTargetService#replaceRuleGroups}。</li>
 *   <li><b>发不出去的规则不该允许存</b>：THRESHOLD 型缺数据源/指标/条件/阈值时判定侧必然抛错，
 *       而那错只写在调度日志里，界面上看就是一条"已启用但永不告警"的规则。渠道类型同理 ——
 *       拼错的 EMAILL 只会让通知日志多一条"没有名为 EMAILL 的通知实现"。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private static final int MAX_NAME_CHARS = 128;
    private static final int MAX_DESCRIPTION_CHARS = 512;
    private static final int MAX_METRIC_CHARS = 512;

    private static final Set<String> TYPES = new HashSet<String>(Arrays.asList("THRESHOLD", "DERIVATIVE", "COMPOSITE"));
    private static final Set<String> SEVERITIES = new HashSet<String>(Arrays.asList("INFO", "WARNING", "CRITICAL"));
    private static final Set<String> CONDITIONS = new HashSet<String>(Arrays.asList("GT", "GTE", "LT", "LTE", "EQ"));

    private final AlertRuleMapper alertRuleMapper;
    private final ObjectMapper objectMapper;
    private final AlertEvaluator alertEvaluator;
    private final AlertNotifyTargetService targetService;
    private final AlertNotifyGroupService notifyGroupService;
    private final List<AlertNotifier> notifiers;

    @Override
    @Transactional
    public Long create(AlertRuleCreateDTO dto) {
        AlertRule rule = new AlertRule();
        rule.setName(requireName(dto.getName()));
        rule.setDescription(requireDescription(dto.getDescription()));
        rule.setType(requireType(dto.getType(), null));
        rule.setSeverity(requireSeverity(dto.getSeverity(), null));
        rule.setNotifyChannels(toJson(requireChannels(dto.getNotifyChannels())));
        rule.setEnabled(true);
        rule.setDatasourceId(dto.getDatasourceId());
        rule.setMetricExpression(requireMetric(dto.getMetricExpression()));
        rule.setCondition(requireCondition(dto.getCondition()));
        rule.setThreshold(dto.getThreshold());
        rule.setDuration(requireDuration(dto.getDuration()));
        requireEvaluatable(rule);
        alertRuleMapper.insert(rule);
        targetService.replaceRuleGroups(rule.getId(), dto.getNotifyGroupIds());
        log.info("Created alert rule: id={}, name={}, channels={}, groups={}",
                rule.getId(), rule.getName(), rule.getNotifyChannels(), size(dto.getNotifyGroupIds()));
        return rule.getId();
    }

    @Override
    @Transactional
    public void update(AlertRuleUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "更新告警规则必须带 id");
        }
        AlertRule existing = requireRule(dto.getId());
        String type = requireType(dto.getType(), existing.getType());
        String metric = requireMetric(dto.getMetricExpression());
        String condition = requireCondition(dto.getCondition());
        Integer duration = requireDuration(dto.getDuration());

        AlertRule patch = new AlertRule();
        patch.setId(existing.getId());
        patch.setName(requireName(dto.getName()));
        patch.setType(type);
        patch.setSeverity(requireSeverity(dto.getSeverity(), existing.getSeverity()));
        if (dto.getNotifyChannels() != null) {
            patch.setNotifyChannels(toJson(requireChannels(dto.getNotifyChannels())));
        }
        // 可空列一律只进 wrapper 的 SET（实体这边一个都不带）：表单是唯一事实来源，删空就得真的写空
        LambdaUpdateWrapper<AlertRule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertRule::getId, existing.getId());
        wrapper.set(AlertRule::getDescription, requireDescription(dto.getDescription()));
        wrapper.set(AlertRule::getDatasourceId, dto.getDatasourceId());
        wrapper.set(AlertRule::getMetricExpression, metric);
        wrapper.set(AlertRule::getCondition, condition);
        wrapper.set(AlertRule::getThreshold, dto.getThreshold());
        wrapper.set(AlertRule::getDuration, duration);
        // 校验看的是"改完之后这条规则长什么样"，不是"这次提交了哪几个字段"
        requireEvaluatable(effective(existing, patch, dto.getDatasourceId(), dto.getThreshold(),
                metric, condition, duration));
        alertRuleMapper.update(patch, wrapper);
        targetService.replaceRuleGroups(existing.getId(), dto.getNotifyGroupIds());
        log.info("Updated alert rule: id={}", existing.getId());
    }

    @Override
    public AlertRuleVO getById(Long id) {
        AlertRuleVO vo = toVO(requireRule(id));
        decorateGroups(Collections.singletonList(vo));
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AlertRule rule = requireRule(id);
        // alert_rule_notify_group 没有逻辑删除列，规则删了引用行就是真删；
        // 留着的话通知组的删除守卫会一直点名一条早已不存在的规则。
        targetService.clearRuleGroups(id);
        alertRuleMapper.deleteById(id);
        log.info("Deleted alert rule: id={}, name={}", id, rule.getName());
    }

    @Override
    public PageResult<AlertRuleVO> page(PageQuery pageQuery, String keyword, String type, String severity) {
        Page<AlertRule> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AlertRule::getName, keyword.trim());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(AlertRule::getType, type);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertRule::getSeverity, severity);
        }
        wrapper.orderByDesc(AlertRule::getCreateTime);
        Page<AlertRule> result = alertRuleMapper.selectPage(page, wrapper);
        List<AlertRuleVO> records = new ArrayList<>();
        for (AlertRule rule : result.getRecords()) {
            records.add(toVO(rule));
        }
        decorateGroups(records);
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    @Transactional
    public void enable(Long id) {
        setEnabled(requireRule(id), true);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        setEnabled(requireRule(id), false);
    }

    @Override
    public AlertRuleTestVO testRule(Long id) {
        AlertRule rule = requireRule(id);
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

    // ---------------------------------------------------------------- 内部

    private AlertRule requireRule(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "告警规则不存在: " + id);
        }
        return rule;
    }

    private void setEnabled(AlertRule rule, boolean enabled) {
        LambdaUpdateWrapper<AlertRule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertRule::getId, rule.getId());
        wrapper.set(AlertRule::getEnabled, enabled);
        alertRuleMapper.update(null, wrapper);
        log.info("Alert rule enabled={}: id={}", enabled, rule.getId());
    }

    /** patch 与 wrapper 各自只带一部分列，校验要看合起来的完整形状 */
    private AlertRule effective(AlertRule existing, AlertRule patch, Long datasourceId, BigDecimal threshold,
                                String metric, String condition, Integer duration) {
        return AlertRule.builder()
                .id(existing.getId())
                .name(patch.getName())
                .type(patch.getType())
                .severity(patch.getSeverity())
                .notifyChannels(patch.getNotifyChannels() == null
                        ? existing.getNotifyChannels() : patch.getNotifyChannels())
                .datasourceId(datasourceId)
                .metricExpression(metric)
                .condition(condition)
                .threshold(threshold)
                .duration(duration)
                .build();
    }

    private String requireName(String raw) {
        String name = trimToNull(raw);
        if (name == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则名称不能为空");
        }
        if (name.length() > MAX_NAME_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则名称超长（最多 " + MAX_NAME_CHARS + " 字）");
        }
        return name;
    }

    private String requireDescription(String raw) {
        String value = trimToNull(raw);
        if (value != null && value.length() > MAX_DESCRIPTION_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则说明超长（最多 " + MAX_DESCRIPTION_CHARS + " 字）");
        }
        return value;
    }

    private String requireMetric(String raw) {
        String value = trimToNull(raw);
        if (value != null && value.length() > MAX_METRIC_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "指标表达式超长（最多 " + MAX_METRIC_CHARS + " 字）");
        }
        return value;
    }

    private String requireCondition(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (!CONDITIONS.contains(upper)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "比较条件只能是 " + String.join("、", sorted(CONDITIONS)) + "，当前是: " + raw);
        }
        return upper;
    }

    private Integer requireDuration(Integer duration) {
        if (duration == null) {
            return null;
        }
        if (duration < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "持续时长不能为负数（0 表示越界即告警）");
        }
        return duration;
    }

    /** 入参没带类型/级别时沿用原来那条的值（不是塞一个默认进去）：只想改阈值的 PUT 不该顺手把类型换掉 */
    private String requireType(String raw, String fallback) {
        String value = trimToNull(raw);
        if (value == null) {
            return trimToNull(fallback) == null ? AlertEvaluator.TYPE_THRESHOLD : fallback.trim().toUpperCase();
        }
        String upper = value.toUpperCase();
        if (!TYPES.contains(upper)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "规则类型只能是 " + String.join("、", sorted(TYPES)) + "，当前是: " + raw);
        }
        return upper;
    }

    private String requireSeverity(String raw, String fallback) {
        String value = trimToNull(raw);
        if (value == null) {
            return trimToNull(fallback) == null ? "WARNING" : fallback.trim().toUpperCase();
        }
        String upper = value.toUpperCase();
        if (!SEVERITIES.contains(upper)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "严重级别只能是 " + String.join("、", sorted(SEVERITIES)) + "，当前是: " + raw);
        }
        return upper;
    }

    /**
     * 渠道类型收口到注册在案的 notifier：统一转大写存下去，拼错或没接入的当场 400。
     * 派发侧那句"没有名为 X 的通知实现"照旧保留，两道不互斥。
     */
    private List<String> requireChannels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> known = new LinkedHashSet<>();
        for (AlertNotifier notifier : notifiers) {
            known.add(notifier.channel().toUpperCase());
        }
        Set<String> picked = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String item : raw) {
            String value = trimToNull(item);
            if (value == null) {
                continue;
            }
            String upper = value.toUpperCase();
            if (!known.contains(upper)) {
                unknown.add(value);
                continue;
            }
            picked.add(upper);
        }
        if (!unknown.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有这些通知渠道的实现: " + String.join("、", unknown)
                    + "（当前可用: " + String.join("、", sorted(known)) + "）");
        }
        return new ArrayList<>(picked);
    }

    /**
     * 阈值型规则的四个判定输入齐不齐。判据抄 {@link AlertEvaluator#evaluate} 的前四道检查 ——
     * 少任何一个都取不出值。DERIVATIVE/COMPOSITE 不在这里要求：判定侧目前明确不支持它们，
     * 那种规则该收到的提示是"暂不支持自动判定"，而不是"缺少阈值"。
     */
    private void requireEvaluatable(AlertRule rule) {
        if (!AlertEvaluator.TYPE_THRESHOLD.equalsIgnoreCase(rule.getType())) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (rule.getDatasourceId() == null) {
            missing.add("数据源");
        }
        if (!StringUtils.hasText(rule.getMetricExpression())) {
            missing.add("指标表达式");
        }
        if (!StringUtils.hasText(rule.getCondition())) {
            missing.add("比较条件");
        }
        if (rule.getThreshold() == null) {
            missing.add("阈值");
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "阈值型规则缺少: " + String.join("、", missing)
                    + " —— 这样的规则存进去只会每轮判定报错，界面上看是一条\u201c已启用但永不告警\u201d");
        }
    }

    private AlertRuleVO toVO(AlertRule rule) {
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setDescription(rule.getDescription());
        vo.setType(rule.getType());
        vo.setDatasourceId(rule.getDatasourceId());
        vo.setMetricExpression(rule.getMetricExpression());
        vo.setCondition(rule.getCondition());
        vo.setThreshold(rule.getThreshold());
        vo.setDuration(rule.getDuration());
        vo.setSeverity(rule.getSeverity());
        vo.setNotifyChannels(fromJson(rule.getNotifyChannels()));
        vo.setEnabled(rule.getEnabled());
        vo.setTenantId(rule.getTenantId());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    /** 一次查完这批规则的组，再按规则分回去（不给列表页留 N+1） */
    private void decorateGroups(List<AlertRuleVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> ruleIds = new ArrayList<>();
        for (AlertRuleVO vo : vos) {
            ruleIds.add(vo.getId());
        }
        Map<Long, List<Long>> links = targetService.groupIdsOfRules(ruleIds);
        Set<Long> allGroupIds = new LinkedHashSet<>();
        for (List<Long> ids : links.values()) {
            allGroupIds.addAll(ids);
        }
        Map<Long, NotifyGroupOptionVO> options = notifyGroupService.optionsById(allGroupIds);
        for (AlertRuleVO vo : vos) {
            List<Long> ids = links.get(vo.getId());
            List<NotifyGroupOptionVO> groups = new ArrayList<>();
            if (ids != null) {
                for (Long groupId : ids) {
                    NotifyGroupOptionVO option = options.get(groupId);
                    if (option != null) {
                        groups.add(option);
                    }
                }
            }
            vo.setNotifyGroups(groups);
            vo.setNotifyTargetsEmpty(!groups.isEmpty() && !anyReachable(groups));
        }
    }

    private boolean anyReachable(List<NotifyGroupOptionVO> groups) {
        for (NotifyGroupOptionVO group : groups) {
            if (isPositive(group.getEmailCount()) || isPositive(group.getMobileCount())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? Collections.<String>emptyList() : list);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "通知渠道存不下去: " + e.getMessage());
        }
    }

    private List<String> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize notify channels: {}", json, e);
            return Collections.emptyList();
        }
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
