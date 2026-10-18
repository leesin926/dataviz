package com.dataviz.collab.dto;

import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 审批决定附带的意见。走请求体而不是 query 参数：意见会进访问日志，URL 不是写批注的地方。
 */
@Data
public class ApprovalDecisionDTO {

    @Size(max = 512, message = "审批意见不能超过 512 字")
    private String comment;
}
