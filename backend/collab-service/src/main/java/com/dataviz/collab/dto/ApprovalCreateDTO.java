package com.dataviz.collab.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 发起审批。申请人 / 租户由会话注入，不接受请求体传入。
 * <p>表里只有 {@code comment} 一列文本，且它承载的是<b>审批人</b>的意见，所以这里不收"申请说明"：
 * 存进同一列会被审批意见覆盖，不存又会让用户以为写了理由。要理由得先加列。</p>
 */
@Data
public class ApprovalCreateDTO {

    /** 审批对象类型：SCREEN / DASHBOARD / DATASET */
    @NotBlank(message = "审批对象类型不能为空")
    @Size(max = 32, message = "审批对象类型过长")
    private String targetType;

    @NotNull(message = "审批对象 ID 不能为空")
    private Long targetId;
}
