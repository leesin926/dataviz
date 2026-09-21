package com.dataviz.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.entity.QueryHistory;
import com.dataviz.analysis.mapper.QueryHistoryMapper;
import com.dataviz.analysis.service.QueryHistoryService;
import com.dataviz.common.core.result.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryHistoryServiceImpl implements QueryHistoryService {

    private final QueryHistoryMapper queryHistoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Long saveQuery(Long userId, Long tenantId, AnalysisQueryDTO queryDTO, long duration) {
        QueryHistory history = new QueryHistory();
        history.setTenantId(String.valueOf(tenantId));
        history.setUserId(String.valueOf(userId));
        history.setDatasourceId(queryDTO.getDatasetId());
        history.setExecutionTime(duration);
        history.setStatus("SUCCESS");

        try {
            history.setSql(objectMapper.writeValueAsString(queryDTO));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize query DTO", e);
            history.setSql("{}");
        }

        queryHistoryMapper.insert(history);
        return history.getId();
    }

    @Override
    public PageResult<?> getHistory(Long userId, int page, int size) {
        Page<QueryHistory> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<QueryHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QueryHistory::getUserId, userId).orderByDesc(QueryHistory::getCreateTime);
        Page<QueryHistory> result = queryHistoryMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream().map(h -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", h.getId());
            map.put("datasourceId", h.getDatasourceId());
            map.put("sql", h.getSql());
            map.put("executionTime", h.getExecutionTime());
            map.put("createTime", h.getCreateTime());
            return map;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, size);
    }
}
