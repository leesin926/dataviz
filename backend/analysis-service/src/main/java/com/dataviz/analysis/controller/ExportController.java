package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.ExportDTO;
import com.dataviz.analysis.engine.QueryEngine;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import com.opencsv.CSVWriter;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/analysis/export")
@RequiredArgsConstructor
@Tag(name = "Data Export", description = "Export query results to Excel or CSV")
public class ExportController {

    private final QueryEngine queryEngine;

    @PostMapping("/excel")
    @Operation(summary = "Export to Excel", description = "Export query results to XLSX file")
    public void exportToExcel(@RequestBody @Valid ExportDTO exportDTO,
                               @RequestHeader("X-User-Id") String userId,
                               @RequestHeader("X-Tenant-Id") String tenantId,
                               HttpServletResponse response) {
        try {
            QueryResultVO data = queryEngine.execute(exportDTO.getQuery(), Long.valueOf(tenantId), Long.valueOf(userId));

            String fileName = URLEncoder.encode(
                    exportDTO.getFileName() != null ? exportDTO.getFileName() : "export.xlsx",
                    "UTF-8");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");

                // Header row
                Row headerRow = sheet.createRow(0);
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                List<String> columns = data.getColumns();
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columns.get(i));
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                List<Map<String, Object>> rows = data.getRows();
                for (int i = 0; i < rows.size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    Map<String, Object> rowData = rows.get(i);
                    for (int j = 0; j < columns.size(); j++) {
                        Cell cell = row.createCell(j);
                        Object value = rowData.get(columns.get(j));
                        if (value instanceof Number) {
                            Number num = (Number) value;
                            cell.setCellValue(num.doubleValue());
                        } else {
                            cell.setCellValue(value != null ? value.toString() : "");
                        }
                    }
                }

                // Auto-size columns
                for (int i = 0; i < columns.size(); i++) {
                    sheet.autoSizeColumn(i);
                }

                workbook.write(response.getOutputStream());
            }

            log.info("Excel export completed: {} rows", data.getRowCount());
        } catch (Exception e) {
            log.error("Excel export failed", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/csv")
    @Operation(summary = "Export to CSV", description = "Export query results to CSV file")
    public void exportToCsv(@RequestBody @Valid ExportDTO exportDTO,
                             @RequestHeader("X-User-Id") String userId,
                             @RequestHeader("X-Tenant-Id") String tenantId,
                             HttpServletResponse response) {
        try {
            QueryResultVO data = queryEngine.execute(exportDTO.getQuery(), Long.valueOf(tenantId), Long.valueOf(userId));

            String fileName = URLEncoder.encode(
                    exportDTO.getFileName() != null ? exportDTO.getFileName() : "export.csv",
                    "UTF-8");

            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            // Write BOM for Excel compatibility
            response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
                // Header
                writer.writeNext(data.getColumns().toArray(new String[0]));

                // Data rows
                List<Map<String, Object>> rows = data.getRows();
                List<String> cols = data.getColumns();
                for (Map<String, Object> rowMap : rows) {
                    String[] line = cols.stream()
                            .map(col -> {
                                Object v = rowMap.get(col);
                                return v != null ? v.toString() : "";
                            })
                            .toArray(String[]::new);
                    writer.writeNext(line);
                }
            }

            log.info("CSV export completed: {} rows", data.getRowCount());
        } catch (Exception e) {
            log.error("CSV export failed", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }
}
