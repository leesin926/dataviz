package com.dataviz.collab.controller;

import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.service.SubscriptionService;
import com.dataviz.collab.vo.SubscriptionVO;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订阅（{@code /api/collab/subscription}），与 {@code CollabController} 的
 * {@code /api/collab/subscriptions} 同义双份，处置见那边的类注释。
 * <p>一条必须记住的差异：这里的 {@code /list/{userId}} 与 {@code /check?userId=} 的**主体来自入参**，
 * 而 CollabController 那两条取的是当前会话（{@code SecurityContextHolder}）。也就是说拿到
 * {@code collab:read} 的人可以查任意人的订阅。这是 API-34 同类的"主体来源"问题，
 * 收口方式是让前端改打 CollabController 那两条、再退役这里的 —— 属破坏性变更，本轮只记不改。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/collab/subscription")
@RequiredArgsConstructor
@RequiresPermission("collab:read")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @RequiresPermission("collab:write")
    public Long subscribe(@RequestBody SubscriptionDTO dto) {
        return subscriptionService.subscribe(dto);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("collab:write")
    public void unsubscribe(@PathVariable Long id) {
        subscriptionService.unsubscribe(id);
    }

    @GetMapping("/list/{userId}")
    public List<SubscriptionVO> listByUser(@PathVariable Long userId) {
        return subscriptionService.listByUser(userId);
    }

    @GetMapping("/check")
    public boolean isSubscribed(
            @RequestParam Long userId,
            @RequestParam String targetType,
            @RequestParam Long targetId) {
        return subscriptionService.isSubscribed(userId, targetType, targetId);
    }
}
