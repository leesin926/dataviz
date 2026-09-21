package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.engine.QueryEngine;
import com.dataviz.analysis.service.QueryHistoryService;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/analysis/query")
@RequiredArgsConstructor
@Tag(name = "Query Engine", description = "Execute analysis queries, save and view history")
public class QueryController {

    private final QueryEngine queryEngine;
    private final QueryHistoryService queryHistoryService;

    @PostMapping("/execute")
    @Operation(summary = "Execute analysis query", description = "Execute a visual analysis query against a dataset")
    public R<QueryResultVO> executeQuery(@RequestBody @Valid AnalysisQueryDTO queryDTO,
                                                @RequestHeader("X-User-Id") String userId,
                                                @RequestHeader("X-Tenant-Id") String tenantId) {
        long start = System.currentTimeMillis();
        QueryResultVO result = queryEngine.execute(queryDTO, Long.valueOf(tenantId), Long.valueOf(userId));
        long duration = System.currentTimeMillis() - start;
        result.setExecutionTime(duration);

        // Save to query history
        queryHistoryService.saveQuery(Long.valueOf(userId), Long.valueOf(tenantId), queryDTO, duration);

        return R.ok(result);
    }

    @PostMapping("/save")
    @Operation(summary = "Save query", description = "Save a query for later reuse")
    public R<Long> saveQuery(@RequestBody @Valid AnalysisQueryDTO queryDTO,
                                   @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(queryHistoryService.saveQuery(
                Long.valueOf(userId), Long.valueOf(tenantId), queryDTO, 0));
    }

    @GetMapping("/history")
    @Operation(summary = "Get query history", description = "Get recent query history for current user")
    public R<?> getQueryHistory(@RequestHeader("X-User-Id") String userId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(queryHistoryService.getHistory(Long.valueOf(userId), page, size));
    }
}
