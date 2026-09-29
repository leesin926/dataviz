package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.AlertContactDTO;
import com.dataviz.alert.entity.AlertContact;
import com.dataviz.alert.entity.AlertNotifyGroup;
import com.dataviz.alert.entity.AlertNotifyGroupMember;
import com.dataviz.alert.mapper.AlertContactMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMapper;
import com.dataviz.alert.mapper.AlertNotifyGroupMemberMapper;
import com.dataviz.alert.service.AlertContactService;
import com.dataviz.alert.vo.AlertContactVO;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertContactServiceImpl implements AlertContactService {

    private static final int MAX_NAME_CHARS = 64;
    private static final int MAX_REMARK_CHARS = 256;

    /**
     * 宽松到"看起来是个地址"为止：真正判地址可不可达的是 SMTP 服务器，
     * 这里挡的是把姓名填进邮箱栏这类录入事故 —— 不挡的话要等到告警真触发那天才发现收件人是个中文词。
     */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");

    /** 手机号只挡形状（可带 + 前缀，5~20 位数字），不判断运营商号段：国际号码形状多样 */
    private static final Pattern MOBILE = Pattern.compile("^\\+?\\d{5,20}$");

    private final AlertContactMapper contactMapper;
    private final AlertNotifyGroupMapper groupMapper;
    private final AlertNotifyGroupMemberMapper memberMapper;

    @Override
    @Transactional
    public Long create(AlertContactDTO dto) {
        String name = trimToNull(dto.getName());
        String email = normalizeEmail(dto.getEmail());
        String mobile = normalizeMobile(dto.getMobile());
        String remark = trimToNull(dto.getRemark());
        requireReachable(name, email, mobile, remark);
        AlertContact contact = new AlertContact();
        contact.setName(name);
        contact.setEmail(email);
        contact.setMobile(mobile);
        contact.setRemark(remark);
        contact.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        contactMapper.insert(contact);
        log.info("Created alert contact: id={}, name={}", contact.getId(), contact.getName());
        return contact.getId();
    }

    @Override
    @Transactional
    public void update(AlertContactDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "更新联系人必须带 id");
        }
        AlertContact existing = requireContact(dto.getId());
        String name = trimToNull(dto.getName());
        String email = normalizeEmail(dto.getEmail());
        String mobile = normalizeMobile(dto.getMobile());
        String remark = trimToNull(dto.getRemark());
        requireReachable(name, email, mobile, remark);

        AlertContact patch = new AlertContact();
        patch.setId(existing.getId());
        patch.setName(name);
        patch.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        // 可空的三栏（邮箱/手机/备注）必须走 wrapper 显式 SET，一个都不留在实体上：
        // MyBatis-Plus 默认更新策略是 NOT_NULL，实体里的 null 不进 SET ⇒ 界面上"把邮箱清空"点了保存
        // 而库里纹丝不动。表单是唯一事实来源，所以清成 null 也要真的写下去。
        // 反过来，同一列也不能既在实体 SET 又在 wrapper SET —— MySQL 会报"列指定了两次"。
        LambdaUpdateWrapper<AlertContact> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertContact::getId, existing.getId());
        wrapper.set(AlertContact::getEmail, email);
        wrapper.set(AlertContact::getMobile, mobile);
        wrapper.set(AlertContact::getRemark, remark);
        contactMapper.update(patch, wrapper);
        log.info("Updated alert contact: id={}", existing.getId());
    }

    @Override
    public AlertContactVO detail(Long id) {
        return toVO(requireContact(id), groupCountOf(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AlertContact contact = requireContact(id);
        List<String> referrers = groupNamesContaining(id);
        if (!referrers.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "联系人仍被 " + referrers.size() + " 个通知组含着，不能删除: " + String.join("、", referrers)
                            + "（只想让他不再收件的话，改成「停用」即可）");
        }
        contactMapper.deleteById(id);
        log.info("Deleted alert contact: id={}, name={}", id, contact.getName());
    }

    @Override
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        AlertContact contact = requireContact(id);
        LambdaUpdateWrapper<AlertContact> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AlertContact::getId, contact.getId());
        wrapper.set(AlertContact::getEnabled, enabled);
        contactMapper.update(null, wrapper);
        log.info("Alert contact enabled={}: id={}", enabled, id);
    }

    @Override
    public PageResult<AlertContactVO> page(PageQuery pageQuery, String keyword) {
        Page<AlertContact> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AlertContact> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(AlertContact::getName, text)
                    .or().like(AlertContact::getEmail, text)
                    .or().like(AlertContact::getMobile, text));
        }
        wrapper.orderByDesc(AlertContact::getCreateTime);
        Page<AlertContact> result = contactMapper.selectPage(page, wrapper);
        Map<Long, Integer> counts = groupCounts(
                result.getRecords().stream().map(AlertContact::getId).collect(Collectors.toList()));
        List<AlertContactVO> records = new ArrayList<>();
        for (AlertContact contact : result.getRecords()) {
            records.add(toVO(contact, counts.get(contact.getId())));
        }
        return PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public List<AlertContactVO> options() {
        LambdaQueryWrapper<AlertContact> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AlertContact::getName);
        List<AlertContact> contacts = contactMapper.selectList(wrapper);
        Map<Long, Integer> counts = groupCounts(
                contacts.stream().map(AlertContact::getId).collect(Collectors.toList()));
        List<AlertContactVO> vos = new ArrayList<>();
        for (AlertContact contact : contacts) {
            vos.add(toVO(contact, counts.get(contact.getId())));
        }
        return vos;
    }

    // ---------------------------------------------------------------- 内部

    private AlertContact requireContact(Long id) {
        AlertContact contact = contactMapper.selectById(id);
        if (contact == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "联系人不存在: " + id);
        }
        return contact;
    }

    /** 名称必填、地址至少一个：判据写在服务层的原因见 {@code AlertContactDTO} 注释 */
    private void requireReachable(String name, String email, String mobile, String remark) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "联系人姓名不能为空");
        }
        if (name.length() > MAX_NAME_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "联系人姓名超长（最多 " + MAX_NAME_CHARS + " 字）");
        }
        if (remark != null && remark.length() > MAX_REMARK_CHARS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "备注超长（最多 " + MAX_REMARK_CHARS + " 字）");
        }
        if (!StringUtils.hasText(email) && !StringUtils.hasText(mobile)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱与手机号至少填一个：一个可达地址都没有的人，挂进通知组也不会有任何收件");
        }
    }

    private String normalizeEmail(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (!EMAIL.matcher(value).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱地址格式不正确: " + value);
        }
        return value.toLowerCase();
    }

    private String normalizeMobile(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String compact = value.replace(" ", "").replace("-", "");
        if (!MOBILE.matcher(compact).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "手机号格式不正确（只能是 5~20 位数字，可带 + 国家码前缀）: " + value);
        }
        return compact;
    }

    private List<String> groupNamesContaining(Long contactId) {
        LambdaQueryWrapper<AlertNotifyGroupMember> members = new LambdaQueryWrapper<>();
        members.eq(AlertNotifyGroupMember::getContactId, contactId);
        List<AlertNotifyGroupMember> rows = memberMapper.selectList(members);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> groupIds = new LinkedHashSet<>();
        for (AlertNotifyGroupMember row : rows) {
            groupIds.add(row.getGroupId());
        }
        LambdaQueryWrapper<AlertNotifyGroup> groups = new LambdaQueryWrapper<>();
        groups.in(AlertNotifyGroup::getId, groupIds);
        List<String> names = new ArrayList<>();
        for (AlertNotifyGroup group : groupMapper.selectList(groups)) {
            names.add(group.getName() + "(ID " + group.getId() + ")");
        }
        return names;
    }

    /** 一次查成员表算出每个联系人被几个组含着，不给列表页留 N+1 */
    private Map<Long, Integer> groupCounts(Collection<Long> contactIds) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        if (contactIds == null || contactIds.isEmpty()) {
            return counts;
        }
        LambdaQueryWrapper<AlertNotifyGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AlertNotifyGroupMember::getContactId, contactIds);
        Map<Long, Set<Long>> seen = new LinkedHashMap<>();
        for (AlertNotifyGroupMember row : memberMapper.selectList(wrapper)) {
            Set<Long> groups = seen.get(row.getContactId());
            if (groups == null) {
                groups = new LinkedHashSet<>();
                seen.put(row.getContactId(), groups);
            }
            groups.add(row.getGroupId());
        }
        for (Map.Entry<Long, Set<Long>> entry : seen.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private Integer groupCountOf(Long contactId) {
        return groupCounts(Arrays.asList(contactId)).get(contactId);
    }

    private AlertContactVO toVO(AlertContact contact, Integer groupCount) {
        return AlertContactVO.builder()
                .id(contact.getId())
                .name(contact.getName())
                .email(contact.getEmail())
                .mobile(contact.getMobile())
                .remark(contact.getRemark())
                .enabled(contact.getEnabled())
                .createTime(contact.getCreateTime())
                .updateTime(contact.getUpdateTime())
                .groupCount(groupCount == null ? 0 : groupCount)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
