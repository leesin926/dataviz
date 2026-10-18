package com.dataviz.analysis.service;

import com.dataviz.analysis.dto.DataPermRuleDTO;
import com.dataviz.analysis.vo.DataPermExplainVO;
import com.dataviz.analysis.vo.DataPermRuleVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

/**
 * 行列权限规则管理（API-21 的配置面）。
 */
public interface DataPermRuleService {

    Long create(DataPermRuleDTO dto);

    void update(Long id, DataPermRuleDTO dto);

    void delete(Long id);

    DataPermRuleVO detail(Long id);

    PageResult<DataPermRuleVO> list(String keyword, PageQuery pageQuery);

    /** 当前身份在这个数据集上实际命中了什么 —— 存完规则不用靠猜它生不生效 */
    DataPermExplainVO explain(Long datasetId);
}
