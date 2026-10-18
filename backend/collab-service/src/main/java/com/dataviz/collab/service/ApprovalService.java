package com.dataviz.collab.service;

import com.dataviz.collab.dto.ApprovalCreateDTO;
import com.dataviz.collab.dto.ApprovalDecisionDTO;
import com.dataviz.collab.vo.ApprovalVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

public interface ApprovalService {

    /** 发起审批：申请人取当前会话身份，重复提交同一目标的待审申请会被拒 */
    Long apply(ApprovalCreateDTO dto);

    /** 通过。只有待审批的申请能被裁决 */
    ApprovalVO approve(Long id, ApprovalDecisionDTO decision);

    /** 驳回。只有待审批的申请能被裁决 */
    ApprovalVO reject(Long id, ApprovalDecisionDTO decision);

    ApprovalVO detail(Long id);

    /**
     * 分页查询。
     *
     * @param mine true = 只看我发起的；false 时 {@code todoOnly} 为 true 表示待我裁决
     */
    PageResult<ApprovalVO> page(String targetType, Long targetId, Integer status, boolean mine,
                                boolean todoOnly, PageQuery pageQuery);
}
