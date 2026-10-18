package com.dataviz.collab.controller;

import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.vo.CommentVO;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论（{@code /api/collab/comment}）。与 {@code CollabController} 的 {@code /api/collab/comments}
 * 是同义双份，处置见那边的类注释。返回体不走 {@code R<>} 包装，是这一组的历史差异，本轮不动。
 */
@Slf4j
@RestController
@RequestMapping("/api/collab/comment")
@RequiredArgsConstructor
@RequiresPermission("collab:read")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @RequiresPermission("collab:write")
    public Long create(@RequestBody CommentCreateDTO dto) {
        return commentService.create(dto);
    }

    @PutMapping("/{id}")
    @RequiresPermission("collab:write")
    public void update(@PathVariable Long id, @RequestParam String content) {
        commentService.update(id, content);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("collab:write")
    public void delete(@PathVariable Long id) {
        commentService.delete(id);
    }

    @GetMapping("/{id}")
    public CommentVO getById(@PathVariable Long id) {
        return commentService.getById(id);
    }

    @GetMapping("/list")
    public List<CommentVO> listByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetId) {
        return commentService.listByTarget(targetType, targetId);
    }

    @GetMapping("/replies/{parentId}")
    public List<CommentVO> listReplies(@PathVariable Long parentId) {
        return commentService.listReplies(parentId);
    }
}
