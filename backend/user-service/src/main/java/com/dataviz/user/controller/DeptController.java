package com.dataviz.user.controller;

import com.dataviz.common.core.result.R;
import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.service.DeptService;
import com.dataviz.user.vo.DeptTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Department management controller.
 */
@Slf4j
@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "Department tree and CRUD operations")
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @Operation(summary = "Get department tree")
    public R<List<DeptTreeVO>> getDeptTree(@RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(deptService.getDeptTree(Long.valueOf(tenantId)));
    }

    @PostMapping
    @Operation(summary = "Create department")
    public R<Long> createDept(@RequestBody @Valid DeptCreateDTO dto,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return R.ok(deptService.createDept(dto, Long.valueOf(tenantId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update department")
    public R<Void> updateDept(@PathVariable("id") Long id, @RequestBody @Valid DeptCreateDTO dto) {
        deptService.updateDept(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete department")
    public R<Void> deleteDept(@PathVariable("id") Long id) {
        deptService.deleteDept(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID")
    public R<DeptTreeVO> getDeptById(@PathVariable("id") Long id) {
        return R.ok(deptService.getDeptById(id));
    }
}
