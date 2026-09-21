package com.dataviz.datasource.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.datasource.dto.QueryExecuteDTO;
import com.dataviz.datasource.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 服务间只读查询接口：alert（指标取数）、analysis（OLAP 执行）、etl（源端抽取）都从这里走，
 * 避免每个服务各自持有一份 JDBC 连接与口令。
 * <p>
 * 免 JWT 但受三层保护：网关 DENY_LIST 直接 403、{@code internal.api.token} 口令校验、
 * 以及底层 {@link DatasourceService#executeQuery} 自身的只读白名单（SELECT/SHOW/DESCRIBE）。
 * 本服务路由没有 StripPrefix，故路径带 /api（见 D38）。
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource/internal")
@RequiredArgsConstructor
@Tag(name = "内部查询接口", description = "服务间调用的只读 SQL 执行，不对外")
public class InternalDatasourceController {

    /** 内部调用同样是不可信输入：不夹紧的话，一个 maxRows=1e7 就能把本服务的堆打满 */
    private static final int MAX_ROWS_CEILING = 5000;
    private static final int TIMEOUT_CEILING = 30;

    private final DatasourceService datasourceService;

    @PostMapping("/query")
    @Operation(summary = "执行只读查询（服务间）")
    public R<Map<String, Object>> query(@RequestBody @Valid QueryExecuteDTO dto) {
        dto.setMaxRows(clamp(dto.getMaxRows(), 1000, MAX_ROWS_CEILING));
        dto.setTimeout(clamp(dto.getTimeout(), 30, TIMEOUT_CEILING));
        log.info("内部查询: datasourceId={}, maxRows={}", dto.getDatasourceId(), dto.getMaxRows());
        return R.ok(datasourceService.executeQuery(dto));
    }

    private int clamp(Integer value, int defaultValue, int ceiling) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, ceiling);
    }
}
