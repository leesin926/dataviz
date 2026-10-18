package com.dataviz.datasource.controller;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import com.dataviz.datasource.dto.QueryExecuteDTO;
import com.dataviz.datasource.dto.WriteBatchDTO;
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

    /** 单批写入行数上限，与 {@code DatasourceWriteClient.BATCH_ROW_CEILING} 同值 */
    private static final int MAX_BATCH_ROWS = 2000;

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

    /**
     * 结构化批量写入（ETL 目标端）。
     * <p>
     * 单批上限是<b>拒绝而非夹紧</b>：查询通道把 maxRows 夹到天花板只是少读几行，写入通道照这个办
     * 等于把调用方以为写完的数据悄悄丢掉一半，症状是"目标表行数对不上但任务全绿"。
     * 超上限就让调用方分批 —— {@code DatasourceWriteClient} 自己会切。
     */
    @PostMapping("/write")
    @Operation(summary = "结构化批量写入（服务间，仅 INSERT）")
    public R<Integer> write(@RequestBody @Valid WriteBatchDTO dto) {
        int rowCount = dto.getRows() == null ? 0 : dto.getRows().size();
        if (rowCount > MAX_BATCH_ROWS) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "单批最多 " + MAX_BATCH_ROWS + " 行，本次 " + rowCount + " 行，请分批调用");
        }
        log.info("内部批量写入: datasourceId={}, table={}, columns={}, rows={}",
                dto.getDatasourceId(), dto.getTable(), dto.getColumns().size(), rowCount);
        return R.ok(datasourceService.writeBatch(dto));
    }
}
