package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertContact;
import com.dataviz.alert.entity.AlertNotifyGroup;
import com.dataviz.alert.entity.AlertNotifyGroupMember;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.AlertRuleNotifyGroup;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertContactMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMemberMapper;
import com.dataviz.alert.mapper.AlertRuleNotifyGroupMapper;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotifyTargetServiceImpl implements AlertNotifyTargetService {

    /**
     * 渠道配置里历史上放收件人的那几个键。
     * <p>
     * 保存侧已经把收件人从渠道表单里拿掉了，但 {@code NotifyChannelServiceImpl.normalize} 会
     * <b>保留未声明的键</b>（否则"打开表单点保存"会把没渲染的字段冲没），所以老行里这几个键照旧存在。
     * 留着这条回退是为了让<b>升级当天没有一条规则突然发不出去</b>；它不是长期形状，
     * 来源会标在 {@link NotifyTargets#source()} 上，界面要显式说"这是渠道里存的历史收件人"。
     */
    private static final String[] LEGACY_EMAIL_KEYS = {"to"};
    private static final String[] LEGACY_MOBILE_KEYS = {"receivers", "mobiles", "atMobiles"};

    private final AlertContactMapper contactMapper;
    private final AlertNotifyGroupMapper groupMapper;
    private final AlertNotifyGroupMemberMapper memberMapper;
    private final AlertRuleNotifyGroupMapper ruleGroupMapper;
    private final ObjectMapper objectMapper;

    @Override
    public NotifyTargets resolveTargets(AlertRule rule, NotifyChannel channel) {
        List<Long> groupIds = groupIdsOf(rule.getId());
        NotifyTargets fromGroups = groupIds.isEmpty()
                ? NotifyTargets.none()
                : resolveFromGroups(groupIds);
        if (fromGroups.hasEmails() || fromGroups.hasMobiles()) {
            return fromGroups;
        }
        NotifyTargets legacy = resolveLegacy(channel);
        if (legacy.hasEmails() || legacy.hasMobiles()) {
            log.info("通知对象回退到渠道存量收件人: ruleId={}, channelId={}, type={}, recipients={}",
                    rule.getId(), channel == null ? null : channel.getId(),
                    channel == null ? null : channel.getType(), legacy.describe());
            return legacy;
        }
        return NotifyTargets.none();
    }

    @Override
    public List<Long> groupIdsOf(Long ruleId) {
        if (ruleId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AlertRuleNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRuleNotifyGroup::getRuleId, ruleId);
        wrapper.orderByAsc(AlertRuleNotifyGroup::getGroupId);
        return distinctGroupIds(ruleGroupMapper.selectList(wrapper));
    }

    @Override
    public Map<Long, List<Long>> groupIdsOfRules(Collection<Long> ruleIds) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        if (ruleIds == null || ruleIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<AlertRuleNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertRuleNotifyGroup::getRuleId, ruleIds);
        wrapper.orderByAsc(AlertRuleNotifyGroup::getGroupId);
        for (AlertRuleNotifyGroup link : ruleGroupMapper.selectList(wrapper)) {
            List<Long> ids = result.get(link.getRuleId());
            if (ids == null) {
                ids = new ArrayList<>();
                result.put(link.getRuleId(), ids);
            }
            if (!ids.contains(link.getGroupId())) {
                ids.add(link.getGroupId());
            }
        }
        return result;
    }

    @Override
    public Map<Long, String> namesOfGroups(Collection<Long> groupIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return names;
        }
        LambdaQueryWrapper<AlertNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertNotifyGroup::getId, groupIds);
        for (AlertNotifyGroup group : groupMapper.selectList(wrapper)) {
            names.put(group.getId(), group.getName());
        }
        return names;
    }

    @Override
    @Transactional
    public void replaceRuleGroups(Long ruleId, List<Long> groupIds) {
        if (groupIds == null) {
            return;
        }
        clearRuleGroups(ruleId);
        if (groupIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long groupId : groupIds) {
            if (groupId != null) {
                distinct.add(groupId);
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        Set<Long> known = namesOfGroups(distinct).keySet();
        List<Long> missing = new ArrayList<>();
        for (Long groupId : distinct) {
            if (!known.contains(groupId)) {
                missing.add(groupId);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "通知组不存在，无法保存: " + join(missing));
        }
        for (Long groupId : distinct) {
            AlertRuleNotifyGroup link = new AlertRuleNotifyGroup();
            link.setRuleId(ruleId);
            link.setGroupId(groupId);
            ruleGroupMapper.insert(link);
        }
    }

    @Override
    public void clearRuleGroups(Long ruleId) {
        if (ruleId == null) {
            return;
        }
        LambdaQueryWrapper<AlertRuleNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRuleNotifyGroup::getRuleId, ruleId);
        ruleGroupMapper.delete(wrapper);
    }

    private NotifyTargets resolveFromGroups(List<Long> groupIds) {
        LambdaQueryWrapper<AlertNotifyGroup> enabled = new LambdaQueryWrapper<>();
        enabled.in(AlertNotifyGroup::getId, groupIds);
        enabled.eq(AlertNotifyGroup::getEnabled, true);
        List<Long> openGroupIds = new ArrayList<>();
        for (AlertNotifyGroup group : groupMapper.selectList(enabled)) {
            openGroupIds.add(group.getId());
        }
        if (openGroupIds.isEmpty()) {
            return NotifyTargets.none();
        }
        LambdaQueryWrapper<AlertNotifyGroupMember> members = new LambdaQueryWrapper<>();
        members.in(AlertNotifyGroupMember::getGroupId, openGroupIds);
        Set<Long> contactIds = new LinkedHashSet<>();
        for (AlertNotifyGroupMember member : memberMapper.selectList(members)) {
            contactIds.add(member.getContactId());
        }
        if (contactIds.isEmpty()) {
            return NotifyTargets.none();
        }
        LambdaQueryWrapper<AlertContact> contacts = new LambdaQueryWrapper<>();
        contacts.in(AlertContact::getId, contactIds);
        contacts.eq(AlertContact::getEnabled, true);
        List<String> emails = new ArrayList<>();
        List<String> mobiles = new ArrayList<>();
        for (AlertContact contact : contactMapper.selectList(contacts)) {
            if (StringUtils.hasText(contact.getEmail())) {
                emails.add(contact.getEmail().trim());
            }
            if (StringUtils.hasText(contact.getMobile())) {
                mobiles.add(contact.getMobile().trim());
            }
        }
        return NotifyTargets.of(emails, mobiles, NotifyTargets.Source.GROUP);
    }

    private NotifyTargets resolveLegacy(NotifyChannel channel) {
        Map<String, Object> config = parseConfigQuietly(channel);
        if (config.isEmpty()) {
            return NotifyTargets.none();
        }
        List<String> emails = new ArrayList<>();
        for (String key : LEGACY_EMAIL_KEYS) {
            emails.addAll(readValues(config, key));
        }
        List<String> mobiles = new ArrayList<>();
        for (String key : LEGACY_MOBILE_KEYS) {
            mobiles.addAll(readValues(config, key));
        }
        return NotifyTargets.of(emails, mobiles, NotifyTargets.Source.CHANNEL_CONFIG);
    }

    /**
     * 老配置可能不是合法 JSON。这里<b>不抛</b>：回退解析拿不到东西就回空，
     * 让后面的"渠道必填项缺失"那条更准确的规定性错误去报 —— 派发侧的异常语义是
     * "这条渠道发不出去"，而不是"收件人解析器崩了"。
     */
    private Map<String, Object> parseConfigQuietly(NotifyChannel channel) {
        if (channel == null || !StringUtils.hasText(channel.getConfig())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(channel.getConfig(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** 存量数据里收件人既可能是数组也可能是逗号串（早期是 SQL 直接塞的），两种都认 */
    private List<String> readValues(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                addIfText(values, item);
            }
        } else {
            for (String part : String.valueOf(value).split("[,;\\n]")) {
                addIfText(values, part);
            }
        }
        return values;
    }

    private void addIfText(List<String> target, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.add(String.valueOf(value).trim());
        }
    }

    private List<Long> distinctGroupIds(List<AlertRuleNotifyGroup> links) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AlertRuleNotifyGroup link : links) {
            if (link.getGroupId() != null) {
                ids.add(link.getGroupId());
            }
        }
        List<Long> sorted = new ArrayList<Long>(ids);
        Collections.sort(sorted);
        return sorted;
    }

    private String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
