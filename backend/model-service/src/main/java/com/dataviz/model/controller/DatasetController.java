package com.dataviz.model.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.model.dto.DatasetCreateDTO;
import com.dataviz.model.dto.DatasetPreviewDTO;
import com.dataviz.model.service.DatasetService;
import com.dataviz.model.vo.DatasetPreviewVO;
import com.dataviz.model.vo.DatasetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 数据集管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model/dataset")
@RequiredArgsConstructor
@Tag(name = "数据集管理", description = "数据集CRUD及预览")
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping
    @Operation(summary = "创建数据集")
    public R<Long> create(@RequestBody @Valid DatasetCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(datasetService.createDataset(dto, tenantId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据集")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid DatasetCreateDTO dto) {
        datasetService.updateDataset(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据集")
    public R<Void> delete(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取数据集详情")
    public R<DatasetVO> getById(@PathVariable Long id) {
        return R.ok(datasetService.getDatasetById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询数据集列表")
    public R<PageResult<DatasetVO>> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(datasetService.listDatasets(tenantId, pageQuery));
    }

    @PostMapping("/preview")
    @Operation(summary = "预览数据集")
    public R<DatasetPreviewVO> preview(@RequestBody @Valid DatasetPreviewDTO dto) {
        return R.ok(datasetService.previewDataset(dto.getDatasetId(), dto.getLimit()));
    }
}
