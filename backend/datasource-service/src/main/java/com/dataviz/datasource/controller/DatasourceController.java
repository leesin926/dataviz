package com.dataviz.datasource.controller;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.datasource.dto.*;
import com.dataviz.datasource.service.DatasourceService;
import com.dataviz.datasource.vo.ColumnInfoVO;
import com.dataviz.datasource.vo.DatasourceListVO;
import com.dataviz.datasource.vo.DatasourceVO;
import com.dataviz.datasource.vo.TableInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
@Tag(name = "数据源管理", description = "数据源CRUD、连接测试、元数据查询、SQL执行")
// 类级 = 本控制器全部端点至少要 datasource:read（PermissionInterceptor 会回落到类上的注解）。
// 只有"改配置"与"让服务端拿着凭据对外建连"这两类动作抬到 write —— 后者是 SSRF 面，只读用户不该能触发。
@RequiresPermission("datasource:read")
public class DatasourceController {

    private final DatasourceService datasourceService;

    @PostMapping
    @RequiresPermission("datasource:write")
    @Operation(summary = "创建数据源")
    public R<Long> create(@RequestBody @Valid DatasourceCreateDTO dto,
                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return R.ok(datasourceService.createDatasource(dto, tenantId));
    }

    @PutMapping("/{id}")
    @RequiresPermission("datasource:write")
    @Operation(summary = "更新数据源")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid DatasourceUpdateDTO dto) {
        datasourceService.updateDatasource(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("datasource:write")
    @Operation(summary = "删除数据源")
    public R<Void> delete(@PathVariable Long id) {
        datasourceService.deleteDatasource(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取数据源详情")
    public R<DatasourceVO> getById(@PathVariable Long id) {
        return R.ok(datasourceService.getDatasourceById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询数据源列表")
    public R<PageResult<DatasourceListVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            PageQuery pageQuery) {
        return R.ok(datasourceService.listDatasources(name, type, status, tenantId, pageQuery));
    }

    @PostMapping("/test-connection")
    @RequiresPermission("datasource:write")
    @Operation(summary = "测试新数据源连接")
    public R<Boolean> testConnection(@RequestBody @Valid TestConnectionDTO dto) {
        return R.ok(datasourceService.testConnection(dto));
    }

    @PostMapping("/{id}/test-connection")
    @RequiresPermission("datasource:write")
    @Operation(summary = "测试已有数据源连接")
    public R<Boolean> testExistingConnection(@PathVariable Long id) {
        return R.ok(datasourceService.testConnection(id));
    }

    @GetMapping("/{id}/tables")
    @Operation(summary = "获取数据源中的表列表")
    public R<List<TableInfoVO>> getTables(@PathVariable Long id) {
        return R.ok(datasourceService.getTables(id));
    }

    @GetMapping("/{id}/tables/{tableName}/columns")
    @Operation(summary = "获取表的列信息")
    public R<List<ColumnInfoVO>> getTableColumns(@PathVariable Long id,
                                                  @PathVariable String tableName) {
        return R.ok(datasourceService.getTableColumns(id, tableName));
    }

    @PostMapping("/execute")
    @Operation(summary = "执行只读查询")
    public R<Map<String, Object>> executeQuery(@RequestBody @Valid QueryExecuteDTO dto) {
        return R.ok(datasourceService.executeQuery(dto));
    }
}
