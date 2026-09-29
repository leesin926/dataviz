package com.dataviz.alert.controller;

import com.dataviz.alert.dto.AlertContactDTO;
import com.dataviz.alert.service.AlertContactService;
import com.dataviz.alert.vo.AlertContactVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警联系人（通知对象的最小单位）。
 * <p>
 * 权限沿用 {@code alert:read} / {@code alert:write} 两档，不新增码：这页读到的邮箱与手机号
 * 原先就摆在 {@code notify_channel.config} 的 {@code to} / {@code receivers} 里，
 * 同一批人同一档可读面，<b>没有因为这次搬迁变大</b>；不新增码也就避免了"权限表改了但没人重登"
 * 那一类只在运行时才显形的缺口。
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/contact")
@RequiredArgsConstructor
@RequiresPermission("alert:read")
public class AlertContactController {

    private final AlertContactService alertContactService;

    @GetMapping("/page")
    public R<PageResult<AlertContactVO>> page(PageQuery pageQuery,
                                              @RequestParam(required = false) String keyword) {
        return R.ok(alertContactService.page(pageQuery, keyword));
    }

    /** 通知组成员选择器用的全量选项 */
    @GetMapping("/options")
    public R<List<AlertContactVO>> options() {
        return R.ok(alertContactService.options());
    }

    @GetMapping("/{id}")
    public R<AlertContactVO> detail(@PathVariable Long id) {
        return R.ok(alertContactService.detail(id));
    }

    @PostMapping
    @RequiresPermission("alert:write")
    public R<Long> create(@RequestBody AlertContactDTO dto) {
        return R.ok(alertContactService.create(dto));
    }

    @PutMapping
    @RequiresPermission("alert:write")
    public R<Void> update(@RequestBody AlertContactDTO dto) {
        alertContactService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("alert:write")
    public R<Void> delete(@PathVariable Long id) {
        alertContactService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("alert:write")
    public R<Void> enable(@PathVariable Long id) {
        alertContactService.setEnabled(id, true);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("alert:write")
    public R<Void> disable(@PathVariable Long id) {
        alertContactService.setEnabled(id, false);
        return R.ok();
    }
}
