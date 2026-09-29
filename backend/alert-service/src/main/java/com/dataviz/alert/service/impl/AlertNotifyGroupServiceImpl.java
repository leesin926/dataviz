package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.AlertNotifyGroupDTO;
import com.dataviz.alert.entity.AlertContact;
import com.dataviz.alert.entity.AlertNotifyGroup;
import com.dataviz.alert.entity.AlertNotifyGroupMember;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.AlertRuleNotifyGroup;
import com.dataviz.alert.mapper.AlertContactMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMemberMapper;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.mapper.AlertRuleNotifyGroupMapper;
import com.dataviz.alert.service.AlertNotifyGroupService;
import com.dataviz.alert.vo.AlertContactVO;
import com.dataviz.alert.vo.AlertNotifyGroupVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
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
public class AlertNotifyGroupServiceImpl implements AlertNotifyGroupService {

    private static final int MAX_NAME_CHARS = 128;

    private final AlertNotifyGroupMapper groupMapper;
    private final AlertContactMapper contactMapper;
    private final AlertNotifyGroupMemberMapper memberMapper;
    private final AlertRuleNotifyGroupMapper ruleGroupMapper;
    private final AlertRuleMapper ruleMapper;

    @Override
    @Transactional
    public Long create(AlertNotifyGroupDTO dto) {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setName(requireName(dto.getName()));
        group.setDescription(trimToNull(dto.getDescription()));
        group.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        groupMapper.insert(group);
        replaceMembers(group.getId(), dto.getContactIds());
        log.info("Created notify group: id={}, name={}, members={}",
                group.getId(), group.getName(), size(dto.getContactIds()));
        return group.getId();
    }

    @Override
    @Transactional
    public void update(AlertNotifyGroupDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "更新通知组必须带 id");
        }
        AlertNotifyGroup existing = requireGroup(dto.getId());
        AlertNotifyGroup patch = new AlertNotifyGroup();
        patch.setId(existing.getId());
        patch.setName(requireName(dto.getName()));
        patch.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        // 描述可清空 ⇒ 走 wrapper 显式 SET（同 AlertContactServiceImpl：NOT_NULL 策略下实体里的 null 不会写库）
        LambdaUpdateWrapper<AlertNotifyGroup> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertNotifyGroup::getId, existing.getId());
        wrapper.set(AlertNotifyGroup::getDescription, trimToNull(dto.getDescription()));
        groupMapper.update(patch, wrapper);
        if (dto.getContactIds() != null) {
            replaceMembers(existing.getId(), dto.getContactIds());
        }
        log.info("Updated notify group: id={}", existing.getId());
    }

    @Override
    public AlertNotifyGroupVO detail(Long id) {
        AlertNotifyGroup group = requireGroup(id);
        List<AlertContactVO> members = membersOf(Collections.singletonList(id)).get(id);
        return toVO(group, members == null ? new ArrayList<AlertContactVO>() : members,
                ruleCountOf(Collections.singletonList(id)).get(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AlertNotifyGroup group = requireGroup(id);
        List<String> referrers = ruleNamesReferencing(id);
        if (!referrers.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "通知组仍被 " + referrers.size() + " 条告警规则引用，不能删除: " + String.join("、", referrers)
                            + "（只想让它暂时不收件的话，改成「停用」即可）");
        }
        deleteMembers(id);
        groupMapper.deleteById(id);
        log.info("Deleted notify group: id={}, name={}", id, group.getName());
    }

    @Override
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        AlertNotifyGroup group = requireGroup(id);
        LambdaUpdateWrapper<AlertNotifyGroup> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertNotifyGroup::getId, group.getId());
        wrapper.set(AlertNotifyGroup::getEnabled, enabled);
        groupMapper.update(null, wrapper);
        log.info("Notify group enabled={}: id={}", enabled, id);
    }

    @Override
    public PageResult<AlertNotifyGroupVO> page(PageQuery pageQuery, String keyword) {
        Page<AlertNotifyGroup> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AlertNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(AlertNotifyGroup::getName, text)
                    .or().like(AlertNotifyGroup::getDescription, text));
        }
        wrapper.orderByDesc(AlertNotifyGroup::getCreateTime);
        Page<AlertNotifyGroup> result = groupMapper.selectPage(page, wrapper);
        List<Long> ids = new ArrayList<>();
        for (AlertNotifyGroup group : result.getRecords()) {
            ids.add(group.getId());
        }
        Map<Long, List<AlertContactVO>> members = membersOf(ids);
        Map<Long, Integer> ruleCounts = ruleCountOf(ids);
        List<AlertNotifyGroupVO> records = new ArrayList<>();
        for (AlertNotifyGroup group : result.getRecords()) {
            List<AlertContactVO> groupMembers = members.get(group.getId());
            records.add(toVO(group, groupMembers == null ? new ArrayList<AlertContactVO>() : groupMembers,
                    ruleCounts.get(group.getId())));
        }
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public List<NotifyGroupOptionVO> options() {
        LambdaQueryWrapper<AlertNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AlertNotifyGroup::getName);
        return toOptions(groupMapper.selectList(wrapper));
    }

    @Override
    public Map<Long, NotifyGroupOptionVO> optionsById(Collection<Long> groupIds) {
        Map<Long, NotifyGroupOptionVO> result = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<AlertNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertNotifyGroup::getId, groupIds);
        for (NotifyGroupOptionVO option : toOptions(groupMapper.selectList(wrapper))) {
            result.put(option.getId(), option);
        }
        return result;
    }

    // ---------------------------------------------------------------- 内部

    /** 名字 + 三种计数，判据只写一份：列表页和规则表单看到的是同一套算法 */
    private List<NotifyGroupOptionVO> toOptions(List<AlertNotifyGroup> groups) {
        List<Long> ids = new ArrayList<>();
        for (AlertNotifyGroup group : groups) {
            ids.add(group.getId());
        }
        Map<Long, List<AlertContactVO>> members = membersOf(ids);
        List<NotifyGroupOptionVO> options = new ArrayList<>();
        for (AlertNotifyGroup group : groups) {
            List<AlertContactVO> contacts = members.get(group.getId());
            int emailCount = 0;
            int mobileCount = 0;
            int memberCount = contacts == null ? 0 : contacts.size();
            if (contacts != null && Boolean.TRUE.equals(group.getEnabled())) {
                for (AlertContactVO contact : contacts) {
                    if (!Boolean.TRUE.equals(contact.getEnabled())) {
                        continue;
                    }
                    if (StringUtils.hasText(contact.getEmail())) {
                        emailCount++;
                    }
                    if (StringUtils.hasText(contact.getMobile())) {
                        mobileCount++;
                    }
                }
            }
            options.add(NotifyGroupOptionVO.builder()
                    .id(group.getId())
                    .name(group.getName())
                    .enabled(group.getEnabled())
                    .memberCount(memberCount)
                    .emailCount(emailCount)
                    .mobileCount(mobileCount)
                    .build());
        }
        return options;
    }

    private AlertNotifyGroup requireGroup(Long id) {
        AlertNotifyGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知组不存在: " + id);
        }
        return group;
    }

    private String requireName(String raw) {
        String name = trimToNull(raw);
        if (name == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "通知组名称不能为空");
        }
        if (name.length() > MAX_NAME_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "通知组名称超长（最多 " + MAX_NAME_CHARS + " 字）");
        }
        return name;
    }

    /**
     * 成员整批替换。
     * <p>
     * 入参里的联系人 id 必须存在：静默丢掉不存在的 id 会让界面显示"5 个人"而库里只有 4 个，
     * 而这类差值只有在告警真触发、少了一个人收不到时才被发现。
     */
    private void replaceMembers(Long groupId, List<Long> contactIds) {
        if (contactIds == null) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long contactId : contactIds) {
            if (contactId != null) {
                distinct.add(contactId);
            }
        }
        deleteMembers(groupId);
        if (distinct.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<AlertContact> known = new LambdaQueryWrapper<>();
        known.in(AlertContact::getId, distinct);
        Set<Long> existing = new LinkedHashSet<>();
        for (AlertContact contact : contactMapper.selectList(known)) {
            existing.add(contact.getId());
        }
        List<Long> missing = new ArrayList<>();
        for (Long contactId : distinct) {
            if (!existing.contains(contactId)) {
                missing.add(contactId);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "联系人不存在，无法加入通知组: " + join(missing));
        }
        for (Long contactId : distinct) {
            AlertNotifyGroupMember member = new AlertNotifyGroupMember();
            member.setGroupId(groupId);
            member.setContactId(contactId);
            memberMapper.insert(member);
        }
    }

    private void deleteMembers(Long groupId) {
        LambdaQueryWrapper<AlertNotifyGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertNotifyGroupMember::getGroupId, groupId);
        memberMapper.delete(wrapper);
    }

    /** groupId → 组内联系人（按加入顺序，含停用的联系人）；一次查完，不给列表页留 N+1 */
    private Map<Long, List<AlertContactVO>> membersOf(Collection<Long> groupIds) {
        Map<Long, List<AlertContactVO>> result = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<AlertNotifyGroupMember> memberQuery = new LambdaQueryWrapper<>();
        memberQuery.in(AlertNotifyGroupMember::getGroupId, groupIds);
        memberQuery.orderByAsc(AlertNotifyGroupMember::getId);
        List<AlertNotifyGroupMember> links = memberMapper.selectList(memberQuery);
        if (links.isEmpty()) {
            return result;
        }
        Set<Long> contactIds = new LinkedHashSet<>();
        for (AlertNotifyGroupMember link : links) {
            contactIds.add(link.getContactId());
        }
        LambdaQueryWrapper<AlertContact> contactQuery = new LambdaQueryWrapper<>();
        contactQuery.in(AlertContact::getId, contactIds);
        Map<Long, AlertContactVO> contacts = new LinkedHashMap<>();
        for (AlertContact contact : contactMapper.selectList(contactQuery)) {
            contacts.put(contact.getId(), toContactVO(contact));
        }
        for (AlertNotifyGroupMember link : links) {
            AlertContactVO contact = contacts.get(link.getContactId());
            if (contact == null) {
                // 联系人被删过而成员行没清干净（历史脏数据）：跳过而不是让整页 500
                continue;
            }
            List<AlertContactVO> bucket = result.get(link.getGroupId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                result.put(link.getGroupId(), bucket);
            }
            bucket.add(contact);
        }
        return result;
    }

    private Map<Long, Integer> ruleCountOf(Collection<Long> groupIds) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return counts;
        }
        LambdaQueryWrapper<AlertRuleNotifyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertRuleNotifyGroup::getGroupId, groupIds);
        Map<Long, Set<Long>> seen = new LinkedHashMap<>();
        for (AlertRuleNotifyGroup link : ruleGroupMapper.selectList(wrapper)) {
            Set<Long> rules = seen.get(link.getGroupId());
            if (rules == null) {
                rules = new LinkedHashSet<>();
                seen.put(link.getGroupId(), rules);
            }
            rules.add(link.getRuleId());
        }
        for (Map.Entry<Long, Set<Long>> entry : seen.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private List<String> ruleNamesReferencing(Long groupId) {
        LambdaQueryWrapper<AlertRuleNotifyGroup> links = new LambdaQueryWrapper<>();
        links.eq(AlertRuleNotifyGroup::getGroupId, groupId);
        List<AlertRuleNotifyGroup> rows = ruleGroupMapper.selectList(links);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> ruleIds = new LinkedHashSet<>();
        for (AlertRuleNotifyGroup row : rows) {
            ruleIds.add(row.getRuleId());
        }
        LambdaQueryWrapper<AlertRule> rules = new LambdaQueryWrapper<>();
        rules.in(AlertRule::getId, ruleIds);
        List<String> names = new ArrayList<>();
        for (AlertRule rule : ruleMapper.selectList(rules)) {
            names.add(rule.getName() + "(ID " + rule.getId() + ")");
        }
        return names;
    }

    private AlertNotifyGroupVO toVO(AlertNotifyGroup group, List<AlertContactVO> members, Integer ruleCount) {
        return AlertNotifyGroupVO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .enabled(group.getEnabled())
                .createTime(group.getCreateTime())
                .updateTime(group.getUpdateTime())
                .members(members)
                .ruleCount(ruleCount == null ? 0 : ruleCount)
                .build();
    }

    private AlertContactVO toContactVO(AlertContact contact) {
        return AlertContactVO.builder()
                .id(contact.getId())
                .name(contact.getName())
                .email(contact.getEmail())
                .mobile(contact.getMobile())
                .remark(contact.getRemark())
                .enabled(contact.getEnabled())
                .createTime(contact.getCreateTime())
                .updateTime(contact.getUpdateTime())
                .groupCount(0)
                .build();
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

    private int size(Collection<?> values) {
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
