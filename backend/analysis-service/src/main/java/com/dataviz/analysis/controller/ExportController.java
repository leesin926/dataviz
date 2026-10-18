package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.dto.ExportDTO;
import com.dataviz.analysis.engine.QueryEngine;
import com.dataviz.analysis.vo.QueryResultVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.security.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opencsv.CSVWriter;

/**
 * 查询结果导出（Excel / CSV）。
 * <p>
 * 两件这里必须说真话的地方：
 * <ol>
 *   <li><b>单次查询有 5000 行天花板</b>（datasource-service 侧强制），所以"导出全部"是分页取、流式写，
 *       不是一次 SQL 拖全表；</li>
 *   <li><b>行数与是否截断写在响应头里</b>（X-Export-Rows / X-Export-Truncated）。为了在写完之前不把响应提交出去，
 *       两种格式都先落到一个临时文件，头知道了才回给浏览器 —— 一个"看起来是全部"的文件比明确的半截文件更坏。</li>
 * </ol>
 * 异常一律原样抛给 GlobalExceptionHandler，不再包成 RuntimeException：包了之后 BizException 的 403/400
 * 语义会变成 500，用户看到的是"导出失败"而不是"你无权访问这个字段"。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/export")
@RequiredArgsConstructor
@Tag(name = "Data Export", description = "Export query results to Excel or CSV")
// 类级 = 本控制器全部端点至少要 analysis:read（PermissionInterceptor 会回落到类上的注解），改状态的动作在方法上抬到 analysis:write。
@RequiresPermission("analysis:read")
public class ExportController {

    private final QueryEngine queryEngine;

    /** 每页行数取数据源的天花板：一页一次往返，总量靠 OFFSET 往前推 */
    private static final int PAGE_ROWS = 5000;

    /** 一次导出请求的行数上限。到顶就停并把截断显式回给调用方 */
    private static final int MAX_EXPORT_ROWS = 100000;

    /** Excel 单元格文本的硬上限，超过会让 POI 抛异常并把整个导出带崩 */
    private static final int EXCEL_CELL_TEXT_LIMIT = 32767;

    /** 与 SqlBuilder 的清洗口径对齐：补出来的排序字段必须本来就是合法标识符 */
    private static final Pattern IDENTIFIER_SAFE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @PostMapping("/excel")
    @RequiresPermission("analysis:write")
    @Operation(summary = "Export to Excel", description = "Export query results to XLSX file")
    public void exportToExcel(@RequestBody @Valid ExportDTO exportDTO,
                              @RequestHeader(value = "X-User-Id", required = false) String userId,
                              @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                              HttpServletResponse response) throws IOException {
        Long user = parseId(userId, "X-User-Id");
        Long tenant = parseId(tenantId, "X-Tenant-Id");
        writeTo(response, exportDTO, tenant, user, "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ExcelExportWriter());
    }

    @PostMapping("/csv")
    @RequiresPermission("analysis:write")
    @Operation(summary = "Export to CSV", description = "Export query results to CSV file")
    public void exportToCsv(@RequestBody @Valid ExportDTO exportDTO,
                            @RequestHeader(value = "X-User-Id", required = false) String userId,
                            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                            HttpServletResponse response) throws IOException {
        Long user = parseId(userId, "X-User-Id");
        Long tenant = parseId(tenantId, "X-Tenant-Id");
        writeTo(response, exportDTO, tenant, user, "csv", "text/csv; charset=UTF-8", new CsvExportWriter());
    }

    /**
     * 先写临时文件、再提交响应：头必须在第一个字节之前定下来，而行数与截断只有写完才知道。
     */
    private void writeTo(HttpServletResponse response, ExportDTO exportDTO, Long tenantId, Long userId,
                         String extension, String contentType, PageWriter writer) throws IOException {
        if (exportDTO.getQuery() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "导出缺少查询配置");
        }
        File temp = File.createTempFile("analysis-export-", "." + extension);
        ExportStat stat;
        try {
            writer.prepare(temp);
            try {
                stat = forEachPage(exportDTO, tenantId, userId, writer);
            } finally {
                writer.finish();
            }
        } catch (IOException | RuntimeException e) {
            quietDelete(temp);
            throw e;
        }

        try {
            String fileName = fileName(exportDTO.getFileName(), extension);
            response.setContentType(contentType);
            // 老浏览器读 filename，新的读 filename*（RFC 5987），中文名两边都能正常显示
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"export." + extension + "\"; filename*=UTF-8''" + encodeFileName(fileName));
            response.setHeader("X-Export-Rows", String.valueOf(stat.rows));
            response.setHeader("X-Export-Truncated", String.valueOf(stat.truncated));
            response.setContentLengthLong(temp.length());
            try (OutputStream out = new BufferedOutputStream(response.getOutputStream())) {
                Files.copy(temp.toPath(), out);
            }
            log.info("导出完成: format={}, rows={}, truncated={}", extension, stat.rows, stat.truncated);
        } finally {
            quietDelete(temp);
        }
    }

    /** 逐页取数并交给 writer；返回实际导出行数与是否被上限截断 */
    private ExportStat forEachPage(ExportDTO exportDTO, Long tenantId, Long userId, PageWriter writer) throws IOException {
        AnalysisQueryDTO query = exportDTO.getQuery();
        if (!Boolean.TRUE.equals(exportDTO.getAllRows())) {
            QueryResultVO page = queryEngine.execute(query, tenantId, userId);
            writer.start(page.getColumns());
            writer.write(page);
            return new ExportStat(rows(page).size(), page.isTruncated());
        }

        // 探针页只为拿到结果列名，好把分页次序定下来；真正的行从 offset 0 重取，不复用这一页
        QueryResultVO probe = queryEngine.execute(query, tenantId, userId, 1, 0);
        List<String> columns = probe.getColumns() == null ? Collections.<String>emptyList() : probe.getColumns();
        writer.start(columns);
        if (!applyStableOrder(query, columns)) {
            // 补不出全序（例如中文列名过不了标识符白名单）时分页会重复/漏行，而这两样从行数上看不出来，
            // 所以只取满一页就停，并让 X-Export-Truncated 说实话
            QueryResultVO page = queryEngine.execute(query, tenantId, userId, PAGE_ROWS, 0);
            writer.write(page);
            return new ExportStat(rows(page).size(), true);
        }

        long written = 0;
        int offset = 0;
        while (true) {
            QueryResultVO page = queryEngine.execute(query, tenantId, userId, PAGE_ROWS, offset);
            writer.write(page);
            int size = rows(page).size();
            written += size;
            if (size < PAGE_ROWS) {
                return new ExportStat(written, false);
            }
            offset += PAGE_ROWS;
            if (written >= MAX_EXPORT_ROWS || page.isTruncated()) {
                return new ExportStat(written, true);
            }
        }
    }

    /**
     * LIMIT/OFFSET 分页在没有全序时是未定义行为：同一个 offset 在两次请求里可能落到不同的行，
     * 拼出来的文件会既有重复又有缺失，而这两样从行数上都看不出来。
     * <p>所以调用方没给排序时，用结果列本身补一份全序（含聚合别名，MySQL 的 ORDER BY 认别名）。
     * 调用方自带的排序保持原样 —— 它可能不唯一，但那是显式选择，不该被后端悄悄换掉。</p>
     *
     * @return 是否拿到了能进 SQL 的全序；false 表示结果列名一个都过不了标识符白名单，调用方须换策略
     */
    private boolean applyStableOrder(AnalysisQueryDTO query, List<String> columns) {
        if (query.getOrders() != null && !query.getOrders().isEmpty()) {
            return true;
        }
        List<AnalysisQueryDTO.OrderConfig> orders = new ArrayList<>(columns.size());
        for (String column : columns) {
            if (column == null || !IDENTIFIER_SAFE.matcher(column).matches()) {
                continue;
            }
            AnalysisQueryDTO.OrderConfig order = new AnalysisQueryDTO.OrderConfig();
            order.setField(column);
            order.setDirection("ASC");
            orders.add(order);
        }
        if (orders.isEmpty()) {
            return columns.isEmpty();
        }
        query.setOrders(orders);
        return true;
    }

    private static List<Map<String, Object>> rows(QueryResultVO page) {
        return page.getRows() == null ? Collections.<Map<String, Object>>emptyList() : page.getRows();
    }

    private static Long parseId(String raw, String headerName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少身份头 " + headerName + "，无法确定导出的数据范围");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, headerName + " 不是合法的数字: " + raw);
        }
    }

    private static String fileName(String raw, String extension) {
        String cleaned = raw == null ? "" : raw.trim();
        if (!cleaned.isEmpty()) {
            // 文件名要进 Content-Disposition：引号、分号和控制字符一律去掉，反斜杠归一成正斜杠
            cleaned = cleaned.replaceAll("[\"\\\\;\r\n]", "").replace('/', '_');
            if (cleaned.toLowerCase().endsWith("." + extension)) {
                return cleaned;
            }
        }
        if (cleaned.isEmpty()) {
            cleaned = "export";
        }
        return cleaned + "." + extension;
    }

    /** RFC 5987 的 filename* 段：URLEncoder 是 application/x-www-form-urlencoded，空格会变成 + 必须换回 %20 */
    private static String encodeFileName(String fileName) throws IOException {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private static void quietDelete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    /** 一页一页往目的地写的目标；prepare 先给目的地，因为 Excel 要在写完才知道有多少行 */
    private interface PageWriter {
        void prepare(File target) throws IOException;

        void start(List<String> columns) throws IOException;

        void write(QueryResultVO page) throws IOException;

        void finish() throws IOException;
    }

    private static final class ExportStat {
        private final long rows;
        private final boolean truncated;

        private ExportStat(long rows, boolean truncated) {
            this.rows = rows;
            this.truncated = truncated;
        }
    }

    /**
     * SXSSF 而不是 XSSFWorkbook：窗口外的行落到磁盘临时文件，
     * 十万行导出不会把整个结果表压在堆里（原先是 XSSF 全内存 + autoSizeColumn 逐列量宽）。
     */
    private static final class ExcelExportWriter implements PageWriter {
        private static final int ROW_WINDOW = 200;
        private static final int DEFAULT_COLUMN_WIDTH = 18 * 256;

        private SXSSFWorkbook workbook;
        private Sheet sheet;
        private File target;
        private List<String> columns = Collections.emptyList();
        private int rowIndex;

        @Override
        public void prepare(File target) {
            this.target = target;
            this.workbook = new SXSSFWorkbook(ROW_WINDOW);
        }

        @Override
        public void start(List<String> columns) {
            this.columns = columns == null ? Collections.<String>emptyList() : columns;
            this.sheet = workbook.createSheet("Data");
            for (int i = 0; i < this.columns.size(); i++) {
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
            }
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (int i = 0; i < this.columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(this.columns.get(i));
                cell.setCellStyle(headerStyle);
            }
            rowIndex = 1;
        }

        @Override
        public void write(QueryResultVO page) {
            for (Map<String, Object> data : rows(page)) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    setCell(row.createCell(i), data.get(columns.get(i)));
                }
            }
        }

        private static void setCell(Cell cell, Object value) {
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                cell.setCellValue((Boolean) value);
            } else {
                String text = String.valueOf(value);
                cell.setCellValue(text.length() > EXCEL_CELL_TEXT_LIMIT ? text.substring(0, EXCEL_CELL_TEXT_LIMIT) : text);
            }
        }

        @Override
        public void finish() throws IOException {
            try (OutputStream out = new FileOutputStream(target)) {
                workbook.write(out);
            } finally {
                // dispose 删掉 SXSSF 的磁盘临时表，close 不带走它们
                workbook.dispose();
                workbook.close();
            }
        }
    }

    /**
     * CSV 落盘后再回给浏览器，理由同 Excel：行数与截断要在提交响应前写进头里。
     * <p>以 {@code = + - @} 开头的单元格会被 Excel 当公式解析（CSV 注入），这里前缀一个单引号让它落成文本 ——
     * XLSX 那边写的是字符串单元格，Excel 不会二次解析，所以只有这一侧需要处理。</p>
     */
    private static final class CsvExportWriter implements PageWriter {
        private static final char[] FORMULA_PREFIXES = {'=', '+', '-', '@', '\t', '\r'};

        private Writer holder;
        private CSVWriter writer;
        private List<String> columns = Collections.emptyList();

        @Override
        public void prepare(File target) throws IOException {
            // Excel 打开 UTF-8 CSV 要靠 BOM 才认编码，少了它中文列全是乱码
            this.holder = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8);
            this.holder.write('\uFEFF');
            this.writer = new CSVWriter(holder);
        }

        @Override
        public void start(List<String> columns) throws IOException {
            this.columns = columns == null ? Collections.<String>emptyList() : columns;
            writer.writeNext(this.columns.toArray(new String[0]));
        }

        @Override
        public void write(QueryResultVO page) throws IOException {
            for (Map<String, Object> data : rows(page)) {
                String[] line = new String[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    Object value = data.get(columns.get(i));
                    line[i] = value == null ? "" : guard(value.toString());
                }
                writer.writeNext(line);
            }
        }

        private static String guard(String text) {
            if (text.isEmpty()) {
                return text;
            }
            for (char prefix : FORMULA_PREFIXES) {
                if (text.charAt(0) == prefix) {
                    return "'" + text;
                }
            }
            return text;
        }

        @Override
        public void finish() throws IOException {
            writer.close();
            holder.close();
        }
    }
}
