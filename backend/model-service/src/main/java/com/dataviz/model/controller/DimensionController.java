package com.dataviz.model.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.model.dto.DimensionCreateDTO;
import com.dataviz.model.service.DimensionService;
import com.dataviz.model.vo.DimensionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 维度管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model/dimension")
@RequiredArgsConstructor
@Tag(name = "维度管理", description = "维度CRUD操作")
// 类级 = 本控制器全部端点至少要 model:read（PermissionInterceptor 会回落到类上的注解），新增/改动/删除定义的动作在方法上抬到 model:write。
@RequiresPermission("model:read")
public class DimensionController {

    private final DimensionService dimensionService;

    @PostMapping
    @RequiresPermission("model:write")
    @Operation(summary = "创建维度")
    public R<Long> create(@RequestBody @Valid DimensionCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(dimensionService.createDimension(dto, tenantId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("model:write")
    @Operation(summary = "更新维度")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid DimensionCreateDTO dto) {
        dimensionService.updateDimension(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("model:write")
    @Operation(summary = "删除维度")
    public R<Void> delete(@PathVariable Long id) {
        dimensionService.deleteDimension(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取维度详情")
    public R<DimensionVO> getById(@PathVariable Long id) {
        return R.ok(dimensionService.getDimensionById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询维度列表")
    public R<PageResult<DimensionVO>> list(
            @RequestParam(required = false) Long datasourceId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(dimensionService.listDimensions(datasourceId, tenantId, pageQuery));
    }
}
