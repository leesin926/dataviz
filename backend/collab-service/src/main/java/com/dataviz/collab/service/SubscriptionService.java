package com.dataviz.collab.service;

import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.vo.SubscriptionVO;

import java.util.List;

public interface SubscriptionService {

    Long subscribe(SubscriptionDTO dto);

    void unsubscribe(Long id);

    List<SubscriptionVO> listByUser(Long userId);

    boolean isSubscribed(Long userId, String targetType, Long targetId);
}
