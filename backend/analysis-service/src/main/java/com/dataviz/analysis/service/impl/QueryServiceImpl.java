package com.dataviz.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.analysis.dto.QueryExecuteDTO;
import com.dataviz.analysis.vo.QueryHistoryVO;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.analysis.entity.QueryHistory;
import com.dataviz.analysis.mapper.QueryHistoryMapper;
import com.dataviz.analysis.service.QueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final QueryHistoryMapper queryHistoryMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public QueryResultVO executeQuery(QueryExecuteDTO dto, String userId, String tenantId) {
        if (dto.getSql() == null || dto.getSql().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "查询 SQL 不能为空（本接口只接受裸 SQL，数据源 ID 必填）");
        }
        // 安全检查: 只允许SELECT语句
        String sql = dto.getSql().trim();
        String upperSql = sql.toUpperCase();
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("SHOW") && !upperSql.startsWith("DESCRIBE")) {
            throw new BizException(ErrorCode.SQL_SYNTAX_ERROR, "只允许执行只读查询语句 (SELECT/SHOW/DESCRIBE)");
        }

        QueryResultVO resultVO = new QueryResultVO();
        resultVO.setSql(sql);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            int maxRows = dto.getMaxRows() != null ? dto.getMaxRows() : 1000;
            // 确保SQL有LIMIT限制
            String limitedSql = sql;
            boolean userSuppliedLimit = upperSql.contains("LIMIT");
            if (!userSuppliedLimit) {
                // 多取一行才能判断截断：读到 maxRows+1 行说明后面还有，正好 maxRows 行则可能是全量
                limitedSql = sql.replaceAll(";\\s*$", "") + " LIMIT " + (maxRows + 1);
            }

            rows = jdbcTemplate.queryForList(limitedSql);
            boolean truncated = !userSuppliedLimit && rows.size() > maxRows;
            if (truncated) {
                rows = rows.subList(0, maxRows);
            }

            if (!rows.isEmpty()) {
                columnNames = new ArrayList<>(rows.get(0).keySet());
            }

            long executionTime = System.currentTimeMillis() - startTime;
            resultVO.setColumns(columnNames);
            resultVO.setRows(rows);
            resultVO.setRowCount(rows.size());
            resultVO.setTruncated(truncated);
            resultVO.setExecutionTime(executionTime);

            // 保存到历史记录
            saveToHistory(dto, resultVO, userId, tenantId);

        } catch (Exception e) {
            log.error("Query execution failed: sql={}", sql, e);
            // 记录失败的查询到历史
            QueryResultVO failedResult = new QueryResultVO();
            failedResult.setSql(sql);
            failedResult.setRowCount(0);
            failedResult.setExecutionTime(System.currentTimeMillis() - startTime);
            saveFailedHistory(dto, failedResult, userId, tenantId);

            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "查询执行失败: " + e.getMessage());
        }

        return resultVO;
    }

    @Override
    public void saveToHistory(QueryExecuteDTO dto, QueryResultVO result, String userId, String tenantId) {
        QueryHistory history = new QueryHistory();
        history.setUserId(userId);
        history.setDatasourceId(dto.getDatasourceId());
        history.setSql(dto.getSql());
        history.setStatus("SUCCESS");
        history.setExecutionTime(result.getExecutionTime());
        history.setRowCount(result.getRowCount());
        history.setTenantId(tenantId);
        queryHistoryMapper.insert(history);
    }

    private void saveFailedHistory(QueryExecuteDTO dto, QueryResultVO result, String userId, String tenantId) {
        try {
            QueryHistory history = new QueryHistory();
            history.setUserId(userId);
            history.setDatasourceId(dto.getDatasourceId());
            history.setSql(dto.getSql());
            history.setStatus("FAILED");
            history.setExecutionTime(result.getExecutionTime());
            history.setRowCount(0);
            history.setTenantId(tenantId);
            queryHistoryMapper.insert(history);
        } catch (Exception e) {
            log.error("Failed to save query history", e);
        }
    }

    @Override
    public PageResult<QueryHistoryVO> getHistory(String userId, String tenantId, PageQuery pageQuery) {
        Page<QueryHistory> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<QueryHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QueryHistory::getTenantId, tenantId);
        if (userId != null && !userId.trim().isEmpty()) {
            wrapper.eq(QueryHistory::getUserId, userId);
        }
        wrapper.orderByDesc(QueryHistory::getCreateTime);

        Page<QueryHistory> result = queryHistoryMapper.selectPage(page, wrapper);
        List<QueryHistoryVO> voList = result.getRecords().stream().map(h -> {
            QueryHistoryVO vo = new QueryHistoryVO();
            vo.setId(h.getId());
            vo.setUserId(h.getUserId());
            vo.setDatasourceId(h.getDatasourceId());
            vo.setSql(h.getSql());
            vo.setStatus(h.getStatus());
            vo.setExecutionTime(h.getExecutionTime());
            vo.setRowCount(h.getRowCount());
            vo.setCreateTime(h.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }
}
