package com.dataviz.alert.controller;

import com.dataviz.alert.dto.AlertRuleCreateDTO;
import com.dataviz.alert.dto.AlertRuleUpdateDTO;
import com.dataviz.alert.service.AlertRuleService;
import com.dataviz.alert.vo.AlertRuleTestVO;
import com.dataviz.alert.vo.AlertRuleVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/alert/rule")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @PostMapping
    public R<Long> create(@RequestBody AlertRuleCreateDTO dto) {
        return R.ok(alertRuleService.create(dto));
    }

    @PutMapping
    public R<Void> update(@RequestBody AlertRuleUpdateDTO dto) {
        alertRuleService.update(dto);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<AlertRuleVO> getById(@PathVariable Long id) {
        return R.ok(alertRuleService.getById(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    public R<PageResult<AlertRuleVO>> page(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String severity) {
        return R.ok(alertRuleService.page(pageQuery, keyword, type, severity));
    }

    @PostMapping("/{id}/enable")
    public R<Void> enable(@PathVariable Long id) {
        alertRuleService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        alertRuleService.disable(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<AlertRuleTestVO> testRule(@PathVariable Long id) {
        return R.ok(alertRuleService.testRule(id));
    }
}
