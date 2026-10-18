package com.dataviz.analysis.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * Export request DTO.
 */
@Data
public class ExportDTO {

    /** The analysis query to execute and export */
    @NotNull(message = "Query configuration is required")
    private AnalysisQueryDTO query;

    /** Export file name (optional) */
    private String fileName;

    /** Export format: excel, csv */
    private String format;

    /**
     * true = 导出这条查询能取到的全部行，而不是当前这一屏。
     * <p>单次查询受数据源行数天花板约束，所以全量是分页取、流式写：
     * 一次 HTTP 请求里最多 {@code ExportController.MAX_EXPORT_ROWS} 行，到上限会在响应头里显形，
     * 不会静默给一个"看起来是全部"的文件。</p>
     */
    private Boolean allRows;
}
