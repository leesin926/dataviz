package com.dataviz.collab.controller;

import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.service.SubscriptionService;
import com.dataviz.collab.vo.SubscriptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/collab/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public Long subscribe(@RequestBody SubscriptionDTO dto) {
        return subscriptionService.subscribe(dto);
    }

    @DeleteMapping("/{id}")
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
