package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.collab.dto.ApprovalCreateDTO;
import com.dataviz.collab.dto.ApprovalDecisionDTO;
import com.dataviz.collab.entity.CollabApproval;
import com.dataviz.collab.enums.ApprovalStatus;
import com.dataviz.collab.mapper.ApprovalMapper;
import com.dataviz.collab.service.ApprovalService;
import com.dataviz.collab.vo.ApprovalVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批流水的服务层：身份从会话取、状态只能单向流转、裁决用条件更新挡住并发重复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalMapper approvalMapper;

    /** 审批对象类型的闭集。脏类型一旦入库，列表里就多一条谁也认领不了的流水 */
    private static final Set<String> TARGET_TYPES =
            new HashSet<String>(Arrays.asList("SCREEN", "DASHBOARD", "DATASET"));

    @Override
    @Transactional
    public Long apply(ApprovalCreateDTO dto) {
        LoginUser current = requireLogin();
        String targetType = dto.getTargetType().trim().toUpperCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "不支持的审批对象类型: " + dto.getTargetType() + "，可选 " + TARGET_TYPES);
        }
        Long tenantId = requireTenant(current);

        // 同一个人对同一个目标重复提审，症状是待办列表里一堆一模一样的单子；直接拒掉而不是再插一条
        Long existing = approvalMapper.selectCount(new LambdaQueryWrapper<CollabApproval>()
                .eq(CollabApproval::getTargetType, targetType)
                .eq(CollabApproval::getTargetId, dto.getTargetId())
                .eq(CollabApproval::getApplicantId, current.getUserId())
                .eq(CollabApproval::getStatus, ApprovalStatus.PENDING.getCode()));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该目标（" + targetType + " " + dto.getTargetId() + "）已有你提交的待审批申请");
        }

        CollabApproval approval = new CollabApproval();
        approval.setTenantId(tenantId);
        approval.setTargetType(targetType);
        approval.setTargetId(dto.getTargetId());
        approval.setApplicantId(current.getUserId());
        approval.setStatus(ApprovalStatus.PENDING.getCode());
        approvalMapper.insert(approval);
        log.info("审批申请已提交: id={}, targetType={}, targetId={}, applicant={}",
                approval.getId(), targetType, dto.getTargetId(), current.getUserId());
        return approval.getId();
    }

    @Override
    public ApprovalVO approve(Long id, ApprovalDecisionDTO decision) {
        return decide(id, ApprovalStatus.APPROVED, decision);
    }

    @Override
    public ApprovalVO reject(Long id, ApprovalDecisionDTO decision) {
        return decide(id, ApprovalStatus.REJECTED, decision);
    }

    /**
     * 裁决走条件更新（{@code where id = ? and status = 待审批}）而不是"读出来改完再写回去"：
     * 两个审批人同时打开同一张待办时，后者读到的是已经改过的行，前一次的裁决会被静默覆盖，
     * 而且覆盖后的审批人/意见是后一个人的，流水看起来完全正常。受影响行数为 0 就说明有人先动了。
     */
    private ApprovalVO decide(Long id, ApprovalStatus target, ApprovalDecisionDTO decision) {
        LoginUser current = requireLogin();
        CollabApproval approval = requireById(id);
        ApprovalStatus from = ApprovalStatus.of(approval.getStatus());
        if (from != ApprovalStatus.PENDING) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该申请已裁决（当前状态：" + label(from) + "），不能重复" + target.getLabel());
        }
        if (!current.isSuperAdmin()) {
            // 自己批自己的申请在语义上不构成审批；超管留一条口子，用于单人租户的演示环境
            requireNotSelf(approval, current, target);
        }

        String comment = decision == null ? null : trimToNull(decision.getComment());
        LocalDateTime now = LocalDateTime.now();
        int updated = approvalMapper.update(null, new LambdaUpdateWrapper<CollabApproval>()
                .eq(CollabApproval::getId, id)
                .eq(CollabApproval::getStatus, ApprovalStatus.PENDING.getCode())
                .set(CollabApproval::getStatus, target.getCode())
                .set(CollabApproval::getApproverId, current.getUserId())
                .set(CollabApproval::getApproveTime, now)
                .set(CollabApproval::getComment, comment));
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "该申请刚被其他人裁决，请刷新后查看");
        }
        log.info("审批已{}: id={}, approver={}", target.getLabel(), id, current.getUserId());
        return toVO(requireById(id));
    }

    private void requireNotSelf(CollabApproval approval, LoginUser current, ApprovalStatus target) {
        if (current.getUserId() != null && current.getUserId().equals(approval.getApplicantId())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    (target == ApprovalStatus.APPROVED ? "通过" : "驳回") + "不能由申请人本人操作");
        }
    }

    @Override
    public ApprovalVO detail(Long id) {
        return toVO(requireById(id));
    }

    @Override
    public PageResult<ApprovalVO> page(String targetType, Long targetId, Integer status, boolean mine,
                                       boolean todoOnly, PageQuery pageQuery) {
        LoginUser current = requireLogin();
        LambdaQueryWrapper<CollabApproval> wrapper = new LambdaQueryWrapper<>();
        if (targetType != null && !targetType.trim().isEmpty()) {
            wrapper.eq(CollabApproval::getTargetType, targetType.trim().toUpperCase());
        }
        if (targetId != null) {
            wrapper.eq(CollabApproval::getTargetId, targetId);
        }
        if (mine) {
            wrapper.eq(CollabApproval::getApplicantId, current.getUserId());
        }
        if (todoOnly) {
            // 待裁决：状态为待审批，且不是我自己提的（我提的自己不能批，列出来只是噪音）
            wrapper.eq(CollabApproval::getStatus, ApprovalStatus.PENDING.getCode());
            if (!current.isSuperAdmin()) {
                wrapper.ne(CollabApproval::getApplicantId, current.getUserId());
            }
        } else if (status != null) {
            if (ApprovalStatus.of(status) == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "未知的审批状态: " + status);
            }
            wrapper.eq(CollabApproval::getStatus, status);
        }
        wrapper.orderByDesc(CollabApproval::getCreateTime).orderByDesc(CollabApproval::getId);

        Page<CollabApproval> page = approvalMapper.selectPage(
                new Page<CollabApproval>(pageQuery.getPageNum(), pageQuery.getPageSize()), wrapper);
        List<ApprovalVO> records = page.getRecords() == null
                ? Collections.<ApprovalVO>emptyList()
                : page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    private CollabApproval requireById(Long id) {
        CollabApproval approval = approvalMapper.selectById(id);
        if (approval == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批申请不存在: " + id);
        }
        return approval;
    }

    private LoginUser requireLogin() {
        LoginUser current = SecurityContextHolder.getLoginUser();
        if (current == null || current.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少登录身份，无法记录申请人 / 审批人");
        }
        return current;
    }

    private Long requireTenant(LoginUser current) {
        String raw = current.getTenantId();
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话缺少租户标识，无法归属审批申请");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话租户标识不是数字: " + raw);
        }
    }

    private ApprovalVO toVO(CollabApproval approval) {
        ApprovalVO vo = new ApprovalVO();
        vo.setId(approval.getId());
        vo.setTenantId(approval.getTenantId());
        vo.setTargetType(approval.getTargetType());
        vo.setTargetId(approval.getTargetId());
        vo.setApplicantId(approval.getApplicantId());
        vo.setApproverId(approval.getApproverId());
        vo.setStatus(approval.getStatus());
        vo.setStatusLabel(label(ApprovalStatus.of(approval.getStatus())));
        vo.setComment(approval.getComment());
        vo.setCreateTime(approval.getCreateTime());
        vo.setApproveTime(approval.getApproveTime());
        return vo;
    }

    private static String label(ApprovalStatus status) {
        return status == null ? "未知" : status.getLabel();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
