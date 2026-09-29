package com.dataviz.alert.controller;

import com.dataviz.alert.dto.AlertNotifyGroupDTO;
import com.dataviz.alert.service.AlertNotifyGroupService;
import com.dataviz.alert.vo.AlertNotifyGroupVO;
import com.dataviz.alert.vo.NotifyGroupOptionVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知组：规则引用的通知单位（详见 {@code AlertNotifyGroup} 的注释）。
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/notify-group")
@RequiredArgsConstructor
@RequiresPermission("alert:read")
public class AlertNotifyGroupController {

    private final AlertNotifyGroupService alertNotifyGroupService;

    @GetMapping("/page")
    public R<PageResult<AlertNotifyGroupVO>> page(PageQuery pageQuery,
                                                  @RequestParam(required = false) String keyword) {
        return R.ok(alertNotifyGroupService.page(pageQuery, keyword));
    }

    /**
     * 规则表单用的组选项（带每组的邮箱数 / 手机数）。
     * <p>
     * 设计端（pc-web）也走这个端点：告警规则和通知组都在 {@code alert:*} 这一档里，
     * 把"能选哪些组"另起一套权限码只会多出一个没人维护的缺口。
     */
    @GetMapping("/options")
    public R<List<NotifyGroupOptionVO>> options() {
        return R.ok(alertNotifyGroupService.options());
    }

    @GetMapping("/{id}")
    public R<AlertNotifyGroupVO> detail(@PathVariable Long id) {
        return R.ok(alertNotifyGroupService.detail(id));
    }

    @PostMapping
    @RequiresPermission("alert:write")
    public R<Long> create(@RequestBody AlertNotifyGroupDTO dto) {
        return R.ok(alertNotifyGroupService.create(dto));
    }

    @PutMapping
    @RequiresPermission("alert:write")
    public R<Void> update(@RequestBody AlertNotifyGroupDTO dto) {
        alertNotifyGroupService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("alert:write")
    public R<Void> delete(@PathVariable Long id) {
        alertNotifyGroupService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("alert:write")
    public R<Void> enable(@PathVariable Long id) {
        alertNotifyGroupService.setEnabled(id, true);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("alert:write")
    public R<Void> disable(@PathVariable Long id) {
        alertNotifyGroupService.setEnabled(id, false);
        return R.ok();
    }
}
