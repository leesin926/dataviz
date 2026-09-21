package com.dataviz.collab.controller;

import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.service.SubscriptionService;
import com.dataviz.collab.vo.CommentVO;
import com.dataviz.collab.vo.SubscriptionVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.context.SecurityContextHolder;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collab")
@RequiredArgsConstructor
public class CollabController {

    private final CommentService commentService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/comments")
    public R<Long> createComment(@Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(commentService.create(dto));
    }

    @PutMapping("/comments/{id}")
    public R<Void> updateComment(@PathVariable Long id, @RequestParam String content) {
        commentService.update(id, content);
        return R.ok();
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        commentService.delete(id);
        return R.ok();
    }

    @GetMapping("/comments/{id}")
    public R<CommentVO> getComment(@PathVariable Long id) {
        return R.ok(commentService.getById(id));
    }

    @GetMapping("/comments/target/{targetType}/{targetId}")
    public R<List<CommentVO>> listCommentsByTarget(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        return R.ok(commentService.listByTarget(targetType, targetId));
    }

    @GetMapping("/comments/replies/{parentId}")
    public R<List<CommentVO>> listReplies(@PathVariable Long parentId) {
        return R.ok(commentService.listReplies(parentId));
    }

    @PostMapping("/subscriptions")
    public R<Long> subscribe(@Valid @RequestBody SubscriptionDTO dto) {
        return R.ok(subscriptionService.subscribe(dto));
    }

    @DeleteMapping("/subscriptions/{id}")
    public R<Void> unsubscribe(@PathVariable Long id) {
        subscriptionService.unsubscribe(id);
        return R.ok();
    }

    @GetMapping("/subscriptions/my")
    public R<List<SubscriptionVO>> listMySubscriptions() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(subscriptionService.listByUser(userId));
    }

    @GetMapping("/subscriptions/check")
    public R<Boolean> isSubscribed(
            @RequestParam String targetType,
            @RequestParam Long targetId) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(subscriptionService.isSubscribed(userId, targetType, targetId));
    }
}
