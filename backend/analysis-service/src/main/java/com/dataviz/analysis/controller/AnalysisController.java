package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.QueryExecuteDTO;
import com.dataviz.analysis.service.QueryService;
import com.dataviz.analysis.vo.QueryHistoryVO;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 分析控制器 - 查询执行和历史
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "数据分析", description = "查询执行、保存查询、历史记录")
public class AnalysisController {

    private final QueryService queryService;

    @PostMapping("/query")
    @Operation(summary = "执行查询")
    public R<QueryResultVO> executeQuery(@RequestBody @Valid QueryExecuteDTO dto,
                                          @RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(queryService.executeQuery(dto, userId, tenantId));
    }

    @GetMapping("/history")
    @Operation(summary = "获取查询历史")
    public R<PageResult<QueryHistoryVO>> getHistory(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(queryService.getHistory(userId, tenantId, pageQuery));
    }
}
