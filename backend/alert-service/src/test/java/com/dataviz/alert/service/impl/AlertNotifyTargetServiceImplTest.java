package com.dataviz.alert.service.impl;

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
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

class AlertNotifyTargetServiceImplTest {

    private AlertContactMapper contactMapper;
    private AlertNotifyGroupMapper groupMapper;
    private AlertNotifyGroupMemberMapper memberMapper;
    private AlertRuleNotifyGroupMapper ruleGroupMapper;
    private AlertNotifyTargetServiceImpl targetService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(
                AlertNotifyGroup.class, AlertNotifyGroupMember.class, AlertContact.class);
    }

    @BeforeEach
    void setUp() {
        contactMapper = mock(AlertContactMapper.class);
        groupMapper = mock(AlertNotifyGroupMapper.class);
        memberMapper = mock(AlertNotifyGroupMemberMapper.class);
        ruleGroupMapper = mock(AlertRuleNotifyGroupMapper.class);
        targetService = new AlertNotifyTargetServiceImpl(
                contactMapper, groupMapper, memberMapper, ruleGroupMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("规则挂启用组时解析出 GROUP 来源收件人")
    void should_resolveGroupTargets_when_ruleHasEnabledGroup() {
        AlertRule rule = new AlertRule();
        rule.setId(1L);

        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setEnabled(true);

        AlertNotifyGroupMember link = new AlertNotifyGroupMember();
        link.setGroupId(10L);
        link.setContactId(100L);

        AlertContact contact = new AlertContact();
        contact.setId(100L);
        contact.setEnabled(true);
        contact.setEmail("a@x.com");
        contact.setMobile("+86138");

        when(ruleGroupMapper.selectList(any())).thenReturn(
                Collections.singletonList(linkRule(1L, 10L)));
        when(groupMapper.selectList(any())).thenReturn(Collections.singletonList(group));
        when(memberMapper.selectList(any())).thenReturn(Collections.singletonList(link));
        when(contactMapper.selectList(any())).thenReturn(Collections.singletonList(contact));

        NotifyTargets targets = targetService.resolveTargets(rule, null);

        assertEquals(NotifyTargets.Source.GROUP, targets.source());
        assertEquals(Collections.singletonList("a@x.com"), targets.emails());
        assertEquals(Collections.singletonList("+86138"), targets.mobiles());
    }

    @Test
    @DisplayName("规则无组时回退渠道配置存量收件人")
    void should_fallbackToChannelConfig_when_noGroupButLegacyConfig() {
        AlertRule rule = new AlertRule();
        rule.setId(2L);

        NotifyChannel channel = new NotifyChannel();
        channel.setConfig("{\"to\":\"legacy@x.com\",\"receivers\":\"13900000000, 13900000001\"}");

        when(ruleGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        NotifyTargets targets = targetService.resolveTargets(rule, channel);

        assertEquals(NotifyTargets.Source.CHANNEL_CONFIG, targets.source());
        assertEquals(Collections.singletonList("legacy@x.com"), targets.emails());
        assertEquals(Arrays.asList("13900000000", "13900000001"), targets.mobiles());
    }

    @Test
    @DisplayName("无组无配置时返回 NONE")
    void should_returnNone_when_noGroupAndNoConfig() {
        AlertRule rule = new AlertRule();
        rule.setId(3L);

        when(ruleGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        NotifyTargets targets = targetService.resolveTargets(rule, null);

        assertEquals(NotifyTargets.Source.NONE, targets.source());
        assertFalse(targets.hasEmails());
        assertFalse(targets.hasMobiles());
    }

    @Test
    @DisplayName("组被停用时拿不到该组联系人")
    void should_returnNone_when_groupDisabled() {
        AlertRule rule = new AlertRule();
        rule.setId(4L);

        when(ruleGroupMapper.selectList(any())).thenReturn(
                Collections.singletonList(linkRule(4L, 10L)));
        // 数据库按 enabled=true 过滤后没有返回任何组
        when(groupMapper.selectList(any())).thenReturn(Collections.emptyList());

        NotifyTargets targets = targetService.resolveTargets(rule, null);

        assertEquals(NotifyTargets.Source.NONE, targets.source());
        verify(memberMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("组内联系人被停用时跳过")
    void should_skipDisabledContact_when_resolvingGroup() {
        AlertRule rule = new AlertRule();
        rule.setId(5L);

        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setEnabled(true);

        AlertNotifyGroupMember link = new AlertNotifyGroupMember();
        link.setGroupId(10L);
        link.setContactId(100L);

        when(ruleGroupMapper.selectList(any())).thenReturn(
                Collections.singletonList(linkRule(5L, 10L)));
        when(groupMapper.selectList(any())).thenReturn(Collections.singletonList(group));
        when(memberMapper.selectList(any())).thenReturn(Collections.singletonList(link));
        // 数据库按 enabled=true 过滤后没有返回被停用的联系人
        when(contactMapper.selectList(any())).thenReturn(Collections.emptyList());

        NotifyTargets targets = targetService.resolveTargets(rule, null);

        assertFalse(targets.hasEmails());
    }

    @Test
    @DisplayName("groupIdsOf 去重并排序")
    void should_returnDistinctSortedIds_when_groupIdsOf() {
        when(ruleGroupMapper.selectList(any())).thenReturn(Arrays.asList(
                linkRule(1L, 30L),
                linkRule(1L, 10L),
                linkRule(1L, 30L),
                linkRule(1L, 20L)));

        List<Long> ids = targetService.groupIdsOf(1L);

        assertEquals(Arrays.asList(10L, 20L, 30L), ids);
    }

    @Test
    @DisplayName("groupIdsOfRules 按规则聚合组 id")
    void should_mapRuleIdToGroupIds_when_groupIdsOfRules() {
        when(ruleGroupMapper.selectList(any())).thenReturn(Arrays.asList(
                linkRule(1L, 10L),
                linkRule(1L, 20L),
                linkRule(2L, 10L)));

        Map<Long, List<Long>> result = targetService.groupIdsOfRules(Arrays.asList(1L, 2L));

        assertEquals(Arrays.asList(10L, 20L), result.get(1L));
        assertEquals(Collections.singletonList(10L), result.get(2L));
    }

    @Test
    @DisplayName("namesOfGroups 返回组 id 到名称的映射")
    void should_returnGroupNames_when_namesOfGroups() {
        AlertNotifyGroup g1 = new AlertNotifyGroup();
        g1.setId(1L);
        g1.setName("运维组");
        AlertNotifyGroup g2 = new AlertNotifyGroup();
        g2.setId(2L);
        g2.setName("值班组");

        when(groupMapper.selectList(any())).thenReturn(Arrays.asList(g1, g2));

        Map<Long, String> names = targetService.namesOfGroups(Arrays.asList(1L, 2L));

        assertEquals("运维组", names.get(1L));
        assertEquals("值班组", names.get(2L));
    }

    @Test
    @DisplayName("replaceRuleGroups 清空旧关联并写入新关联")
    void should_replaceOldLinks_when_replaceRuleGroups() {
        AlertNotifyGroup group = new AlertNotifyGroup();
        group.setId(10L);
        group.setName("运维组");

        when(groupMapper.selectList(any())).thenReturn(Collections.singletonList(group));

        targetService.replaceRuleGroups(1L, Arrays.asList(10L, 10L));

        verify(ruleGroupMapper).delete(any());
        ArgumentCaptor<AlertRuleNotifyGroup> captor = ArgumentCaptor.forClass(AlertRuleNotifyGroup.class);
        verify(ruleGroupMapper, times(1)).insert(captor.capture());
        assertEquals(1L, captor.getValue().getRuleId());
        assertEquals(10L, captor.getValue().getGroupId());
    }

    @Test
    @DisplayName("replaceRuleGroups 传入 null 直接返回")
    void should_doNothing_when_replaceRuleGroupsReceivesNull() {
        targetService.replaceRuleGroups(1L, null);
        verify(ruleGroupMapper, never()).delete(any());
        verify(ruleGroupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("replaceRuleGroups 遇到不存在的组抛异常")
    void should_throwException_when_groupNotExists() {
        when(groupMapper.selectList(any())).thenReturn(Collections.emptyList());

        BizException ex = assertThrows(BizException.class,
                () -> targetService.replaceRuleGroups(1L, Collections.singletonList(99L)));
        assertTrue(ex.getMessage().contains("通知组不存在"));
    }

    @Test
    @DisplayName("clearRuleGroups 删除规则的全部组关联")
    void should_deleteLinks_when_clearRuleGroups() {
        targetService.clearRuleGroups(1L);
        verify(ruleGroupMapper).delete(any());
    }

    private AlertRuleNotifyGroup linkRule(Long ruleId, Long groupId) {
        AlertRuleNotifyGroup link = new AlertRuleNotifyGroup();
        link.setRuleId(ruleId);
        link.setGroupId(groupId);
        return link;
    }
}
