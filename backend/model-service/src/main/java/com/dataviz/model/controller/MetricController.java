package com.dataviz.model.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.model.dto.MetricCreateDTO;
import com.dataviz.model.service.MetricService;
import com.dataviz.model.vo.MetricVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 指标管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model/metric")
@RequiredArgsConstructor
@Tag(name = "指标管理", description = "指标CRUD操作")
// 类级 = 本控制器全部端点至少要 model:read（PermissionInterceptor 会回落到类上的注解），新增/改动/删除定义的动作在方法上抬到 model:write。
@RequiresPermission("model:read")
public class MetricController {

    private final MetricService metricService;

    @PostMapping
    @RequiresPermission("model:write")
    @Operation(summary = "创建指标")
    public R<Long> create(@RequestBody @Valid MetricCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(metricService.createMetric(dto, tenantId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("model:write")
    @Operation(summary = "更新指标")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid MetricCreateDTO dto) {
        metricService.updateMetric(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("model:write")
    @Operation(summary = "删除指标")
    public R<Void> delete(@PathVariable Long id) {
        metricService.deleteMetric(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取指标详情")
    public R<MetricVO> getById(@PathVariable Long id) {
        return R.ok(metricService.getMetricById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询指标列表")
    public R<PageResult<MetricVO>> list(
            @RequestParam(required = false) Long datasourceId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(metricService.listMetrics(datasourceId, tenantId, pageQuery));
    }
}
