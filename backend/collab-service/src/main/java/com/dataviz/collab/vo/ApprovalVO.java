package com.dataviz.collab.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批流水的对外视图。状态同时给码和中文标签，前端不必再维护一份 0/1/2 的对照表。
 */
@Data
public class ApprovalVO {

    private Long id;

    private Long tenantId;

    private String targetType;

    private Long targetId;

    private Long applicantId;

    private Long approverId;

    private Integer status;

    private String statusLabel;

    private String comment;

    private LocalDateTime createTime;

    private LocalDateTime approveTime;
}
