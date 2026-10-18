package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.entity.CollabSubscription;
import com.dataviz.collab.mapper.SubscriptionMapper;
import com.dataviz.collab.service.SubscriptionService;
import com.dataviz.collab.vo.SubscriptionVO;
import com.dataviz.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public Long subscribe(SubscriptionDTO dto) {
        CollabSubscription subscription = new CollabSubscription();
        BeanUtils.copyProperties(dto, subscription);
        // 主体只能取自会话：DTO 里没有 userId，而 user_id 是 NOT NULL 且无默认值 —— 不写这一句，
        // 严格模式下 INSERT 直接报错、非严格模式下落 0，两种结果都是"订阅成功但我的订阅里查不到"。
        subscription.setUserId(SecurityContextHolder.getUserId());
        subscriptionMapper.insert(subscription);
        log.info("Created subscription: id={}", subscription.getId());
        return subscription.getId();
    }

    @Override
    @Transactional
    public void unsubscribe(Long id) {
        subscriptionMapper.deleteById(id);
        log.info("Deleted subscription: id={}", id);
    }

    @Override
    public List<SubscriptionVO> listByUser(Long userId) {
        LambdaQueryWrapper<CollabSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabSubscription::getUserId, userId)
               .orderByDesc(CollabSubscription::getCreateTime);
        List<CollabSubscription> list = subscriptionMapper.selectList(wrapper);
        return list.stream().map(s -> {
            SubscriptionVO vo = new SubscriptionVO();
            BeanUtils.copyProperties(s, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean isSubscribed(Long userId, String targetType, Long targetId) {
        LambdaQueryWrapper<CollabSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabSubscription::getUserId, userId)
               .eq(CollabSubscription::getTargetType, targetType)
               .eq(CollabSubscription::getTargetId, targetId);
        return subscriptionMapper.selectCount(wrapper) > 0;
    }
}
