package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.entity.CollabSubscription;
import com.dataviz.collab.mapper.SubscriptionMapper;
import com.dataviz.collab.test.MybatisPlusTableInfoHelper;
import com.dataviz.collab.vo.SubscriptionVO;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订阅服务：判据集中在"订阅归到谁名下"。user_id 是 NOT NULL 且无默认值，
 * 而 DTO 里根本没有这个字段 —— 不补就等于把"订阅成功、我的订阅里没有"交给用户。
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTableInfoHelper.initFor(CollabSubscription.class);
    }

    @BeforeEach
    void setUpSession() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(7L).username("designer01").tenantId("1").build());
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("订阅 - 主体取自会话，不接受入参")
    void should_stampUserFromSession_when_subscribe() {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setTargetType("screen");
        dto.setTargetId(901L);

        subscriptionService.subscribe(dto);

        ArgumentCaptor<CollabSubscription> saved = ArgumentCaptor.forClass(CollabSubscription.class);
        verify(subscriptionMapper).insert(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals("screen", saved.getValue().getTargetType());
        // 前端刻意不填 notifyTypes：后端零读取方，填了就等于替系统承诺"会收到提醒"
        assertNull(saved.getValue().getNotifyTypes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_filterByGivenUser_when_listByUser() {
        CollabSubscription row = new CollabSubscription();
        row.setId(901L);
        row.setUserId(7L);
        row.setTargetType("screen");
        row.setTargetId(901L);
        when(subscriptionMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row));

        assertEquals(1, subscriptionService.listByUser(7L).size());

        ArgumentCaptor<Wrapper<CollabSubscription>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(subscriptionMapper).selectList(captor.capture());
        Wrapper<CollabSubscription> wrapper = captor.getValue();
        // 必须先逼出 SQL 段：MP 的 eq() 把值塞进 paramNameValuePairs 发生在 formatParam 里，
        // 而那是 ISqlSegment 懒求值 —— 不读 getSqlSegment() 就拿到空 Map。
        // 只断言参数、不断言列名：测试助手里的 Configuration 没开 mapUnderscoreToCamelCase，
        // MP 退化成用属性名拼 SQL，断 "user_id" 会测到助手的配置而不是业务条件。
        String sql = wrapper.getSqlSegment();
        Map<String, Object> pairs =
                ((AbstractWrapper<CollabSubscription, ?, ?>) wrapper).getParamNameValuePairs();
        assertTrue(sql.contains("MPGENVAL"), "我的订阅应按主体加条件: " + sql);
        assertTrue(pairs.containsValue(7L), "我的订阅必须按会话主体过滤: " + pairs);
    }

    @Test
    @DisplayName("取消订阅 - 命中即删")
    void should_deleteById_when_unsubscribe() {
        subscriptionService.unsubscribe(901L);
        verify(subscriptionMapper).deleteById(901L);
    }

    @Test
    void should_returnFalse_when_noSubscriptionRow() {
        when(subscriptionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertFalse(subscriptionService.isSubscribed(7L, "screen", 901L));
    }

    @Test
    void should_returnTrue_when_subscriptionRowExists() {
        when(subscriptionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertTrue(subscriptionService.isSubscribed(7L, "screen", 901L));
    }

    @Test
    @DisplayName("VO 映射 - notifyTypes 为空就留空，页面自己显示「未设置」")
    void should_keepNullNotifyTypes_when_mappingVo() {
        CollabSubscription row = new CollabSubscription();
        row.setId(902L);
        row.setUserId(7L);
        row.setTargetType("dataset");
        row.setTargetId(5L);
        when(subscriptionMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(row));

        SubscriptionVO vo = subscriptionService.listByUser(7L).get(0);

        assertEquals(902L, vo.getId());
        assertNull(vo.getNotifyTypes());
    }
}
