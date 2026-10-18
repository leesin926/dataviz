package com.dataviz.analysis.controller;

import com.dataviz.analysis.dto.DataPermRuleDTO;
import com.dataviz.analysis.service.DataPermRuleService;
import com.dataviz.analysis.vo.DataPermExplainVO;
import com.dataviz.analysis.vo.DataPermRuleVO;
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
 * 行列数据权限规则（API-21 的配置面）。
 * <p>
 * 路由挂在 {@code /api/analysis/**} 下复用现成的网关规则，权限码也复用
 * {@code analysis:read / analysis:write}：配规则是"自助分析"这个能力的一部分，
 * 不是独立业务域，为它新开一码只会让码表和授权树各多一处需要同步的地方。
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/perm-rule")
@RequiredArgsConstructor
@Tag(name = "数据权限规则", description = "行级/列级数据权限的配置与命中回显")
// 类级 = 本控制器全部端点至少要 analysis:read（PermissionInterceptor 会回落到类上的注解），改状态的动作在方法上抬到 analysis:write。
@RequiresPermission("analysis:read")
public class DataPermRuleController {

    private final DataPermRuleService ruleService;

    @PostMapping
    @RequiresPermission("analysis:write")
    @Operation(summary = "新建规则")
    public R<Long> create(@RequestBody @Valid DataPermRuleDTO dto) {
        return R.ok(ruleService.create(dto));
    }

    @PutMapping("/{id}")
    @RequiresPermission("analysis:write")
    @Operation(summary = "更新规则")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid DataPermRuleDTO dto) {
        ruleService.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("analysis:write")
    @Operation(summary = "删除规则")
    public R<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "规则详情")
    public R<DataPermRuleVO> detail(@PathVariable Long id) {
        return R.ok(ruleService.detail(id));
    }

    @GetMapping("/list")
    @Operation(summary = "规则列表")
    public R<PageResult<DataPermRuleVO>> list(@RequestParam(required = false) String keyword,
                                              PageQuery pageQuery) {
        return R.ok(ruleService.list(keyword, pageQuery));
    }

    @GetMapping("/explain")
    @Operation(summary = "命中回显",
            description = "当前登录身份在该数据集上实际命中的规则、渲染出的行谓词与被遮列；不传 datasetId 表示只看全局规则")
    public R<DataPermExplainVO> explain(@RequestParam(required = false) Long datasetId) {
        return R.ok(ruleService.explain(datasetId));
    }
}
