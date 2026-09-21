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
}
