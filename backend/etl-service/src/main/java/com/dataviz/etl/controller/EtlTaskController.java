package com.dataviz.etl.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.etl.dto.EtlTaskCreateDTO;
import com.dataviz.etl.dto.EtlTaskUpdateDTO;
import com.dataviz.etl.service.EtlTaskService;
import com.dataviz.etl.vo.EtlMetricsVO;
import com.dataviz.etl.vo.EtlTaskLogVO;
import com.dataviz.etl.vo.EtlTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ETL任务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/etl/task")
@RequiredArgsConstructor
@Tag(name = "ETL任务管理", description = "ETL任务CRUD、启动/停止/暂停、日志查询、指标统计")
// 类级 = 本控制器全部端点至少要 etl:read（PermissionInterceptor 会回落到类上的注解），改任务与启停/触发任务运行的动作抬到 etl:write。
@RequiresPermission("etl:read")
public class EtlTaskController {

    private final EtlTaskService etlTaskService;

    @PostMapping
    @RequiresPermission("etl:write")
    @Operation(summary = "创建ETL任务")
    public R<Long> create(@RequestBody @Valid EtlTaskCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(etlTaskService.createTask(dto, tenantId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("etl:write")
    @Operation(summary = "更新ETL任务")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid EtlTaskUpdateDTO dto) {
        etlTaskService.updateTask(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("etl:write")
    @Operation(summary = "删除ETL任务")
    public R<Void> delete(@PathVariable Long id) {
        etlTaskService.deleteTask(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情")
    public R<EtlTaskVO> getById(@PathVariable Long id) {
        return R.ok(etlTaskService.getTaskById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询任务列表")
    public R<PageResult<EtlTaskVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(etlTaskService.listTasks(name, status, tenantId, pageQuery));
    }

    @PostMapping("/{id}/start")
    @RequiresPermission("etl:write")
    @Operation(summary = "启动任务")
    public R<Void> start(@PathVariable Long id) {
        etlTaskService.startTask(id);
        return R.ok();
    }

    @PostMapping("/{id}/stop")
    @RequiresPermission("etl:write")
    @Operation(summary = "停止任务")
    public R<Void> stop(@PathVariable Long id) {
        etlTaskService.stopTask(id);
        return R.ok();
    }

    @PostMapping("/{id}/pause")
    @RequiresPermission("etl:write")
    @Operation(summary = "暂停任务")
    public R<Void> pause(@PathVariable Long id) {
        etlTaskService.pauseTask(id);
        return R.ok();
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "获取任务执行日志")
    public R<List<EtlTaskLogVO>> getLogs(@PathVariable Long id) {
        return R.ok(etlTaskService.getTaskLogs(id));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "获取任务指标")
    public R<EtlMetricsVO> getMetrics(@PathVariable Long id) {
        return R.ok(etlTaskService.getTaskMetrics(id));
    }
}
