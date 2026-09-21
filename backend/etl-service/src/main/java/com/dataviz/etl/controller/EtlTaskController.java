package com.dataviz.etl.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
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
public class EtlTaskController {

    private final EtlTaskService etlTaskService;

    @PostMapping
    @Operation(summary = "创建ETL任务")
    public R<Long> create(@RequestBody @Valid EtlTaskCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(etlTaskService.createTask(dto, tenantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新ETL任务")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid EtlTaskUpdateDTO dto) {
        etlTaskService.updateTask(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
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
    @Operation(summary = "启动任务")
    public R<Void> start(@PathVariable Long id) {
        etlTaskService.startTask(id);
        return R.ok();
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "停止任务")
    public R<Void> stop(@PathVariable Long id) {
        etlTaskService.stopTask(id);
        return R.ok();
    }

    @PostMapping("/{id}/pause")
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
