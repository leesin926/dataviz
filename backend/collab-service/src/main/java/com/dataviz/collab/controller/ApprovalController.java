package com.dataviz.collab.controller;

import com.dataviz.collab.dto.ApprovalCreateDTO;
import com.dataviz.collab.dto.ApprovalDecisionDTO;
import com.dataviz.collab.service.ApprovalService;
import com.dataviz.collab.vo.ApprovalVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协作审批。
 * <p>权限用 collab:read / collab:write —— 码表补丁是 {@code deploy/sql/patch/2026-10-16-r1-permission-codes.sql}，
 * 尚未执行。补丁跑完并授权前，除超管外这些端点一律 403。这是有意的失败方向：审批本身就是权限动作，
 * 宁可现在关着，也不要一个谁都能批的审批。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/collab/approval")
@RequiredArgsConstructor
@Tag(name = "Collab Approval", description = "内容上线审批流水")
@RequiresPermission("collab:read")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @RequiresPermission("collab:write")
    @Operation(summary = "发起审批")
    public R<Long> apply(@RequestBody @Valid ApprovalCreateDTO dto) {
        return R.ok(approvalService.apply(dto));
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission("collab:write")
    @Operation(summary = "通过审批")
    public R<ApprovalVO> approve(@PathVariable Long id,
                                 @RequestBody(required = false) ApprovalDecisionDTO decision) {
        return R.ok(approvalService.approve(id, decision));
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission("collab:write")
    @Operation(summary = "驳回审批")
    public R<ApprovalVO> reject(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalDecisionDTO decision) {
        return R.ok(approvalService.reject(id, decision));
    }

    @GetMapping("/{id}")
    @Operation(summary = "审批详情")
    public R<ApprovalVO> detail(@PathVariable Long id) {
        return R.ok(approvalService.detail(id));
    }

    @GetMapping("/list")
    @Operation(summary = "审批流水分页", description = "mine=我发起的，todo=待我裁决")
    public R<PageResult<ApprovalVO>> list(@RequestParam(required = false) String targetType,
                                          @RequestParam(required = false) Long targetId,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false, defaultValue = "false") boolean mine,
                                          @RequestParam(required = false, defaultValue = "false") boolean todo,
                                          PageQuery pageQuery) {
        return R.ok(approvalService.page(targetType, targetId, status, mine, todo, pageQuery));
    }
}
