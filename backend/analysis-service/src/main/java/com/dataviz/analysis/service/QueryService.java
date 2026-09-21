package com.dataviz.analysis.service;

import com.dataviz.analysis.dto.QueryExecuteDTO;
import com.dataviz.analysis.vo.QueryHistoryVO;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;

/**
 * 查询服务接口
 */
public interface QueryService {

    /**
     * 执行查询(带超时和行数限制, 只读)
     */
    QueryResultVO executeQuery(QueryExecuteDTO dto, String userId, String tenantId);

    /**
     * 保存查询到历史记录
     */
    void saveToHistory(QueryExecuteDTO dto, QueryResultVO result, String userId, String tenantId);

    /**
     * 获取查询历史
     */
    PageResult<QueryHistoryVO> getHistory(String userId, String tenantId, PageQuery pageQuery);
}
