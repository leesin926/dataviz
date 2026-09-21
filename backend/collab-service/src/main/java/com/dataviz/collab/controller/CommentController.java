package com.dataviz.collab.controller;

import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/collab/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public Long create(@RequestBody CommentCreateDTO dto) {
        return commentService.create(dto);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestParam String content) {
        commentService.update(id, content);
    }

    @DeleteMapping("/{id}")
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
