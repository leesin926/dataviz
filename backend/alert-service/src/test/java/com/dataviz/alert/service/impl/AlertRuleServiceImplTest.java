package com.dataviz.alert.service.impl;

import com.dataviz.alert.dto.AlertRuleCreateDTO;
import com.dataviz.alert.dto.AlertRuleUpdateDTO;
import com.dataviz.alert.engine.AlertEvaluator;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.service.AlertNotifyGroupService;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.alert.vo.AlertRuleVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertRuleServiceImplTest {

    private AlertRuleMapper alertRuleMapper;
    private ObjectMapper objectMapper;
    private AlertEvaluator alertEvaluator;
    private AlertNotifyTargetService targetService;
    private AlertNotifyGroupService notifyGroupService;
    private AlertNotifier emailNotifier;
    private AlertRuleServiceImpl ruleService;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(AlertRule.class);
    }

    @BeforeEach
    void setUp() {
        alertRuleMapper = mock(AlertRuleMapper.class);
        objectMapper = new ObjectMapper();
        alertEvaluator = mock(AlertEvaluator.class);
        targetService = mock(AlertNotifyTargetService.class);
        notifyGroupService = mock(AlertNotifyGroupService.class);
        emailNotifier = mock(AlertNotifier.class);
        when(emailNotifier.channel()).thenReturn("EMAIL");

        ruleService = new AlertRuleServiceImpl(
                alertRuleMapper,
                objectMapper,
                alertEvaluator,
                targetService,
                notifyGroupService,
                Collections.singletonList(emailNotifier));
    }

    @Test
    @DisplayName("创建阈值规则成功并保存通知组关联")
    void should_createThresholdRule_and_saveGroupLinks() {
        AlertRuleCreateDTO dto = new AlertRuleCreateDTO();
        dto.setName("CPU 高");
        dto.setType("THRESHOLD");
        dto.setSeverity("WARNING");
        dto.setDatasourceId(1L);
        dto.setMetricExpression("cpu_usage");
        dto.setCondition("GT");
        dto.setThreshold(BigDecimal.valueOf(80));
        dto.setNotifyChannels(Collections.singletonList("EMAIL"));
        dto.setNotifyGroupIds(Arrays.asList(10L, 20L));

        when(alertRuleMapper.insert(any(AlertRule.class))).thenAnswer(inv -> {
            AlertRule rule = inv.getArgument(0);
            rule.setId(1L);
            return 1;
        });

        Long id = ruleService.create(dto);

        assertEquals(1L, id);
        verify(targetService).replaceRuleGroups(1L, Arrays.asList(10L, 20L));
        ArgumentCaptor<AlertRule> captor = ArgumentCaptor.forClass(AlertRule.class);
        verify(alertRuleMapper).insert(captor.capture());
        AlertRule saved = captor.getValue();
        assertEquals("CPU 高", saved.getName());
        assertEquals("THRESHOLD", saved.getType());
        assertEquals("[\"EMAIL\"]", saved.getNotifyChannels());
    }

    @Test
    @DisplayName("创建阈值规则缺少必填判定项抛异常")
    void should_throwException_when_thresholdRuleIncomplete() {
        AlertRuleCreateDTO dto = new AlertRuleCreateDTO();
        dto.setName("CPU 高");
        dto.setType("THRESHOLD");
        dto.setDatasourceId(1L);
        dto.setMetricExpression("cpu_usage");
        dto.setNotifyChannels(Collections.emptyList());

        assertThrows(BizException.class, () -> ruleService.create(dto));
        verify(alertRuleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建规则遇到未注册渠道抛异常")
    void should_throwException_when_channelUnknown() {
        AlertRuleCreateDTO dto = new AlertRuleCreateDTO();
        dto.setName("CPU 高");
        dto.setType("THRESHOLD");
        dto.setDatasourceId(1L);
        dto.setMetricExpression("cpu_usage");
        dto.setCondition("GT");
        dto.setThreshold(BigDecimal.valueOf(80));
        dto.setNotifyChannels(Collections.singletonList("UNKNOWN"));

        assertThrows(BizException.class, () -> ruleService.create(dto));
    }

    @Test
    @DisplayName("更新规则替换通知组关联")
    void should_updateRule_and_replaceGroupLinks() {
        AlertRule existing = ruleOf(1L, "CPU 高", "THRESHOLD", "[{\"datasourceId\":1}]");

        AlertRuleUpdateDTO dto = new AlertRuleUpdateDTO();
        dto.setId(1L);
        dto.setName("CPU 高");
        dto.setType("THRESHOLD");
        dto.setSeverity("CRITICAL");
        dto.setDatasourceId(1L);
        dto.setMetricExpression("cpu_usage");
        dto.setCondition("GT");
        dto.setThreshold(BigDecimal.valueOf(90));
        dto.setNotifyChannels(Collections.singletonList("EMAIL"));
        dto.setNotifyGroupIds(Collections.singletonList(30L));

        when(alertRuleMapper.selectById(1L)).thenReturn(existing);
        when(alertRuleMapper.update(any(AlertRule.class), any())).thenReturn(1);

        ruleService.update(dto);

        verify(targetService).replaceRuleGroups(1L, Collections.singletonList(30L));
    }

    @Test
    @DisplayName("删除规则时清掉组关联并删除规则")
    void should_clearGroupsAndDeleteRule_when_delete() {
        AlertRule rule = ruleOf(1L, "CPU 高", "THRESHOLD", "[]");
        when(alertRuleMapper.selectById(1L)).thenReturn(rule);

        ruleService.delete(1L);

        verify(targetService).clearRuleGroups(1L);
        verify(alertRuleMapper).deleteById(1L);
    }

    @Test
    @DisplayName("getById 回填通知组选项")
    void should_decorateGroups_when_getById() {
        AlertRule rule = ruleOf(1L, "CPU 高", "THRESHOLD", "[\"EMAIL\"]");
        when(alertRuleMapper.selectById(1L)).thenReturn(rule);

        Map<Long, java.util.List<Long>> links = new LinkedHashMap<>();
        links.put(1L, Collections.singletonList(10L));
        when(targetService.groupIdsOfRules(Collections.singletonList(1L))).thenReturn(links);

        Map<Long, NotifyGroupOptionVO> options = new LinkedHashMap<>();
        options.put(10L, NotifyGroupOptionVO.builder()
                .id(10L).name("运维组").enabled(true).memberCount(2).emailCount(2).mobileCount(1).build());
        when(notifyGroupService.optionsById(Collections.singleton(10L))).thenReturn(options);

        AlertRuleVO vo = ruleService.getById(1L);

        assertNotNull(vo);
        assertEquals(1, vo.getNotifyGroups().size());
        assertEquals("运维组", vo.getNotifyGroups().get(0).getName());
        assertFalse(vo.getNotifyTargetsEmpty());
    }

    @Test
    @DisplayName("分页回填通知组并标记无有效收件人")
    void should_decorateGroupsAndMarkEmpty_when_page() {
        AlertRule rule = ruleOf(1L, "CPU 高", "THRESHOLD", "[\"EMAIL\"]");
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AlertRule> pageResult =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
        pageResult.setRecords(Collections.singletonList(rule));

        when(alertRuleMapper.selectPage(any(), any())).thenReturn(pageResult);

        Map<Long, java.util.List<Long>> links = new LinkedHashMap<>();
        links.put(1L, Collections.singletonList(10L));
        when(targetService.groupIdsOfRules(Collections.singletonList(1L))).thenReturn(links);

        Map<Long, NotifyGroupOptionVO> options = new LinkedHashMap<>();
        options.put(10L, NotifyGroupOptionVO.builder()
                .id(10L).name("空组").enabled(true).memberCount(0).emailCount(0).mobileCount(0).build());
        when(notifyGroupService.optionsById(Collections.singleton(10L))).thenReturn(options);

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<AlertRuleVO> result = ruleService.page(query, null, null, null);

        assertEquals(1, result.getRecords().size());
        AlertRuleVO vo = result.getRecords().get(0);
        assertEquals(1, vo.getNotifyGroups().size());
        assertTrue(vo.getNotifyTargetsEmpty());
    }

    private AlertRule ruleOf(Long id, String name, String type, String channels) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName(name);
        rule.setType(type);
        rule.setSeverity("WARNING");
        rule.setNotifyChannels(channels);
        rule.setEnabled(true);
        return rule;
    }
}
