package com.dataviz.collab.controller;

import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.dto.SubscriptionDTO;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.service.SubscriptionService;
import com.dataviz.collab.vo.CommentVO;
import com.dataviz.collab.vo.SubscriptionVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.common.security.context.SecurityContextHolder;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论 + 订阅（面向前端的那组路径：{@code /api/collab/comments}、{@code /api/collab/subscriptions}）。
 * <p>注意这里和 {@code CommentController}（{@code /api/collab/comment}）、
 * {@code SubscriptionController}（{@code /api/collab/subscription}）是<b>两套并存的同义端点</b>，
 * 打的是同一批 service 方法。本轮两边都上码但不合并 —— 删掉一套是破坏性变更，需先确认没有调用方。
 * <b>更正（2026-10-16 / AT5）</b>：这一组复数路径已经是前端的唯一调用方（{@code api-client}
 * 的 {@code modules/collab.ts} 引了九个符号，{@code pc-web} 的 {@code CollabDrawer.vue} 在用），
 * 早期台账里"api-client 一条都没引"那句作废。留着两套的代价是同一个动作有两条路可走，
 * 排查"评论是谁发的"要多看一眼路径。</p>
 */
@RestController
@RequestMapping("/api/collab")
@RequiredArgsConstructor
@RequiresPermission("collab:read")
public class CollabController {

    private final CommentService commentService;
    private final SubscriptionService subscriptionService;

    @PostMapping("/comments")
    @RequiresPermission("collab:write")
    public R<Long> createComment(@Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(commentService.create(dto));
    }

    @PutMapping("/comments/{id}")
    @RequiresPermission("collab:write")
    public R<Void> updateComment(@PathVariable Long id, @RequestParam String content) {
        commentService.update(id, content);
        return R.ok();
    }

    @DeleteMapping("/comments/{id}")
    @RequiresPermission("collab:write")
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
    @RequiresPermission("collab:write")
    public R<Long> subscribe(@Valid @RequestBody SubscriptionDTO dto) {
        return R.ok(subscriptionService.subscribe(dto));
    }

    @DeleteMapping("/subscriptions/{id}")
    @RequiresPermission("collab:write")
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
