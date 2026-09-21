package com.dataviz.collab.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.collab.entity.CollabApproval;
import com.dataviz.collab.mapper.ApprovalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/collab/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalMapper approvalMapper;

    @PostMapping
    public Long create(@RequestBody CollabApproval approval) {
        approval.setStatus(0);
        approvalMapper.insert(approval);
        log.info("Created approval: id={}", approval.getId());
        return approval.getId();
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        CollabApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new RuntimeException("Approval not found: " + id);
        }
        approval.setStatus(1);
        approval.setComment(comment);
        approval.setApproveTime(LocalDateTime.now());
        approvalMapper.updateById(approval);
        log.info("Approved: id={}", id);
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        CollabApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new RuntimeException("Approval not found: " + id);
        }
        approval.setStatus(2);
        approval.setComment(comment);
        approval.setApproveTime(LocalDateTime.now());
        approvalMapper.updateById(approval);
        log.info("Rejected: id={}", id);
    }

    @GetMapping("/list")
    public List<CollabApproval> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<CollabApproval> wrapper = new LambdaQueryWrapper<>();
        if (targetType != null) {
            wrapper.eq(CollabApproval::getTargetType, targetType);
        }
        if (targetId != null) {
            wrapper.eq(CollabApproval::getTargetId, targetId);
        }
        if (status != null) {
            wrapper.eq(CollabApproval::getStatus, status);
        }
        wrapper.orderByDesc(CollabApproval::getCreateTime);
        return approvalMapper.selectList(wrapper);
    }
}
