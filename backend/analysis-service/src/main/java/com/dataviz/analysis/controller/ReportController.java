package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.ReportCreateDTO;
import com.dataviz.analysis.dto.ReportUpdateDTO;
import com.dataviz.analysis.service.ReportService;
import com.dataviz.analysis.vo.ReportDataVO;
import com.dataviz.analysis.vo.ReportVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 报告管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/report")
@RequiredArgsConstructor
@Tag(name = "分析报告管理", description = "报告CRUD、发布/取消发布、获取报告数据")
// 类级 = 本控制器全部端点至少要 analysis:read（PermissionInterceptor 会回落到类上的注解），改状态的动作在方法上抬到 analysis:write。
@RequiresPermission("analysis:read")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @RequiresPermission("analysis:write")
    @Operation(summary = "创建报告")
    public R<Long> create(@RequestBody @Valid ReportCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(reportService.createReport(dto, tenantId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("analysis:write")
    @Operation(summary = "更新报告")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid ReportUpdateDTO dto) {
        reportService.updateReport(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("analysis:write")
    @Operation(summary = "删除报告")
    public R<Void> delete(@PathVariable Long id) {
        reportService.deleteReport(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取报告详情")
    public R<ReportVO> getById(@PathVariable Long id) {
        return R.ok(reportService.getReportById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询报告列表")
    public R<PageResult<ReportVO>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(reportService.listReports(tenantId, pageQuery));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("analysis:write")
    @Operation(summary = "发布报告")
    public R<Void> publish(@PathVariable Long id) {
        reportService.publishReport(id);
        return R.ok();
    }

    @PostMapping("/{id}/unpublish")
    @RequiresPermission("analysis:write")
    @Operation(summary = "取消发布报告")
    public R<Void> unpublish(@PathVariable Long id) {
        reportService.unpublishReport(id);
        return R.ok();
    }

    @GetMapping("/{id}/data")
    @Operation(summary = "获取报告数据")
    public R<ReportDataVO> getReportData(@PathVariable Long id) {
        return R.ok(reportService.getReportData(id));
    }
}
