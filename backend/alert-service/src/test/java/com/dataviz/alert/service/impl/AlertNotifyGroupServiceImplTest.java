package com.dataviz.alert.service.impl;

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
import com.dataviz.alert.vo.AlertNotifyGroupVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertNotifyGroupServiceImplTest {

    private AlertNotifyGroupMapper groupMapper;
    private AlertContactMapper contactMapper;
    private AlertNotifyGroupMemberMapper memberMapper;
    private AlertRuleNotifyGroupMapper ruleGroupMapper;
    private AlertRuleMapper ruleMapper;
    private AlertNotifyGroupServiceImpl groupService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(AlertNotifyGroup.class, AlertContact.class);
    }

    @BeforeEach
    void setUp() {
        groupMapper = mock(AlertNotifyGroupMapper.class);
        contactMapper = mock(AlertContactMapper.class);
        memberMapper = mock(AlertNotifyGroupMemberMapper.class);
        ruleGroupMapper = mock(AlertRuleNotifyGroupMapper.class);
        ruleMapper = mock(AlertRuleMapper.class);
        groupService = new AlertNotifyGroupServiceImpl(
                groupMapper, contactMapper, memberMapper, ruleGroupMapper, ruleMapper);
    }

    @Test
    @DisplayName("创建通知组并写入成员")
    void should_createGroupAndMembers_when_inputValid() {
        AlertNotifyGroupDTO dto = new AlertNotifyGroupDTO();
        dto.setName("运维组");
        dto.setDescription("值班");
        dto.setEnabled(true);
        dto.setContactIds(Arrays.asList(1L, 2L));

        AlertContact c1 = contactOf(1L, "a@x.com", "+86138");
        AlertContact c2 = contactOf(2L, "b@x.com", null);
        when(contactMapper.selectList(any())).thenReturn(Arrays.asList(c1, c2));
        when(groupMapper.insert(any(AlertNotifyGroup.class))).thenAnswer(inv -> {
            AlertNotifyGroup g = inv.getArgument(0);
            g.setId(10L);
            return 1;
        });

        Long id = groupService.create(dto);

        assertEquals(10L, id);
        ArgumentCaptor<AlertNotifyGroup> groupCaptor = ArgumentCaptor.forClass(AlertNotifyGroup.class);
        verify(groupMapper).insert(groupCaptor.capture());
        assertEquals("运维组", groupCaptor.getValue().getName());
        assertTrue(groupCaptor.getValue().getEnabled());

        verify(memberMapper).delete(any());
        ArgumentCaptor<AlertNotifyGroupMember> memberCaptor = ArgumentCaptor.forClass(AlertNotifyGroupMember.class);
        verify(memberMapper, times(2)).insert(memberCaptor.capture());
        List<AlertNotifyGroupMember> members = memberCaptor.getAllValues();
        assertEquals(10L, members.get(0).getGroupId());
        assertEquals(1L, members.get(0).getContactId());
        assertEquals(2L, members.get(1).getContactId());
    }

    @Test
    @DisplayName("更新通知组替换成员")
    void should_replaceMembers_when_updateGroup() {
        AlertNotifyGroup existing = new AlertNotifyGroup();
        existing.setId(10L);
        existing.setName("运维组");

        AlertNotifyGroupDTO dto = new AlertNotifyGroupDTO();
        dto.setId(10L);
        dto.setName("运维组新");
        dto.setEnabled(false);
        dto.setContactIds(Collections.singletonList(3L));

        when(groupMapper.selectById(10L)).thenReturn(existing);
        when(contactMapper.selectList(any())).thenReturn(
                Collections.singletonList(contactOf(3L, "c@x.com", "+86139")));
        when(groupMapper.update(any(AlertNotifyGroup.class), any())).thenReturn(1);

        groupService.update(dto);

        ArgumentCaptor<AlertNotifyGroup> patchCaptor = ArgumentCaptor.forClass(AlertNotifyGroup.class);
        verify(groupMapper).update(patchCaptor.capture(), any());
        assertEquals("运维组新", patchCaptor.getValue().getName());
        assertFalse(patchCaptor.getValue().getEnabled());
        verify(memberMapper).delete(any());
        verify(memberMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("更新时 contactIds 为 null 不碰成员")
    void should_notTouchMembers_when_contactIdsNull() {
        AlertNotifyGroup existing = new AlertNotifyGroup();
        existing.setId(10L);
        existing.setName("运维组");

        AlertNotifyGroupDTO dto = new AlertNotifyGroupDTO();
        dto.setId(10L);
        dto.setName("运维组新");
        dto.setContactIds(null);

        when(groupMapper.selectById(10L)).thenReturn(existing);
        when(groupMapper.update(any(AlertNotifyGroup.class), any())).thenReturn(1);

        groupService.update(dto);

        verify(memberMapper, never()).delete(any());
        verify(memberMapper, never()).insert(any());
    }

    @Test
    @DisplayName("删除被规则引用的通知组抛异常")
    void should_throwException_when_deleteReferencedGroup() {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setName("运维组");

        when(groupMapper.selectById(10L)).thenReturn(group);
        when(ruleGroupMapper.selectList(any())).thenReturn(
                Collections.singletonList(linkRule(1L, 10L)));
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setName("CPU 高");
        when(ruleMapper.selectList(any())).thenReturn(Collections.singletonList(rule));

        BizException ex = assertThrows(BizException.class, () -> groupService.delete(10L));
        assertTrue(ex.getMessage().contains("仍被"));
        verify(groupMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除未被引用的通知组成功")
    void should_deleteGroup_when_notReferenced() {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setName("运维组");

        when(groupMapper.selectById(10L)).thenReturn(group);
        when(ruleGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        groupService.delete(10L);

        verify(memberMapper).delete(any());
        verify(groupMapper).deleteById(10L);
    }

    @Test
    @DisplayName("detail 返回成员与规则引用数")
    void should_returnDetail_withMembersAndRuleCount() {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setName("运维组");

        when(groupMapper.selectById(10L)).thenReturn(group);
        when(memberMapper.selectList(any())).thenReturn(
                Collections.singletonList(memberOf(10L, 1L)));
        when(contactMapper.selectList(any())).thenReturn(
                Collections.singletonList(contactOf(1L, "a@x.com", "+86138")));
        when(ruleGroupMapper.selectList(any())).thenReturn(
                Collections.singletonList(linkRule(1L, 10L)));

        AlertNotifyGroupVO vo = groupService.detail(10L);

        assertEquals("运维组", vo.getName());
        assertEquals(1, vo.getMembers().size());
        assertEquals(Integer.valueOf(1), vo.getRuleCount());
    }

    @Test
    @DisplayName("options 统计邮箱/手机/成员数")
    void should_countReachableMembers_when_options() {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setName("运维组");
        group.setEnabled(true);

        when(groupMapper.selectList(any())).thenReturn(Collections.singletonList(group));
        when(memberMapper.selectList(any())).thenReturn(Arrays.asList(
                memberOf(10L, 1L),
                memberOf(10L, 2L),
                memberOf(10L, 3L)));
        when(contactMapper.selectList(any())).thenReturn(Arrays.asList(
                contactOf(1L, "a@x.com", "+86138"),
                contactOf(2L, null, "+86139"),
                contactOf(3L, "c@x.com", null)));

        List<NotifyGroupOptionVO> options = groupService.options();

        assertEquals(1, options.size());
        NotifyGroupOptionVO option = options.get(0);
        assertEquals(Integer.valueOf(3), option.getMemberCount());
        assertEquals(Integer.valueOf(2), option.getEmailCount());
        assertEquals(Integer.valueOf(2), option.getMobileCount());
    }

    private AlertContact contactOf(Long id, String email, String mobile) {
        AlertContact c = new AlertContact();
        c.setId(id);
        c.setName("c" + id);
        c.setEmail(email);
        c.setMobile(mobile);
        c.setEnabled(true);
        return c;
    }

    private AlertNotifyGroupMember memberOf(Long groupId, Long contactId) {
        AlertNotifyGroupMember m = new AlertNotifyGroupMember();
        m.setGroupId(groupId);
        m.setContactId(contactId);
        return m;
    }

    private AlertRuleNotifyGroup linkRule(Long ruleId, Long groupId) {
        AlertRuleNotifyGroup link = new AlertRuleNotifyGroup();
        link.setRuleId(ruleId);
        link.setGroupId(groupId);
        return link;
    }
}
