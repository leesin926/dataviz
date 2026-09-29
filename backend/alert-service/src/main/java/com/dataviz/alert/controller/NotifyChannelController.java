package com.dataviz.alert.controller;

import com.dataviz.alert.dto.ChannelTestDTO;
import com.dataviz.alert.dto.NotifyChannelDTO;
import com.dataviz.alert.service.NotifyChannelService;
import com.dataviz.alert.vo.ChannelSchemaVO;
import com.dataviz.alert.vo.ChannelTestVO;
import com.dataviz.alert.vo.NotifyChannelVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知渠道配置。表单由 {@code /schema} 驱动：前端不写死任何渠道的字段，
 * 因此加一种渠道只需要在后端加一个 {@code @Component}。
 * <p>
 * 这个控制器原先直接操作 mapper 并把实体整条回给调用方 —— 那张表的 {@code config} 里是群机器人 token、
 * SMTP 口令、短信 AK/SK，任何 {@code alert:read} 都能读到。现在读走脱敏 VO，写走服务层校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/channel")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 alert:read（PermissionInterceptor 会回落到类上的注解）；渠道会存下服务端将来 POST 的 URL，写配置属 SSRF 相关面，抬到 alert:write。
@RequiresPermission("alert:read")
public class NotifyChannelController {

    private final NotifyChannelService notifyChannelService;

    /** 各渠道的类型码 + 配置项声明（管理端配置页据此渲染表单） */
    @GetMapping("/schema")
    public R<List<ChannelSchemaVO>> schema() {
        return R.ok(notifyChannelService.schemas());
    }

    @GetMapping("/page")
    public R<PageResult<NotifyChannelVO>> page(PageQuery pageQuery,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String type) {
        return R.ok(notifyChannelService.page(pageQuery, keyword, type));
    }

    @GetMapping("/{id}")
    public R<NotifyChannelVO> getById(@PathVariable Long id) {
        return R.ok(notifyChannelService.detail(id));
    }

    @PostMapping
    @RequiresPermission("alert:write")
    public R<Long> create(@RequestBody NotifyChannelDTO dto) {
        return R.ok(notifyChannelService.create(dto));
    }

    @PutMapping
    @RequiresPermission("alert:write")
    public R<Void> update(@RequestBody NotifyChannelDTO dto) {
        notifyChannelService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("alert:write")
    public R<Void> delete(@PathVariable Long id) {
        notifyChannelService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("alert:write")
    public R<Void> enable(@PathVariable Long id) {
        notifyChannelService.setEnabled(id, true);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("alert:write")
    public R<Void> disable(@PathVariable Long id) {
        notifyChannelService.setEnabled(id, false);
        return R.ok();
    }

    /**
     * 用一条合成消息实测该渠道；失败原因作为结果回，不抛异常（详见服务层注释）。
     * {@code recipients} 是给本次实测的临时收件人；传空则走与真实派发<b>同一条</b>解析路
     * （这条请求没有规则，所以组那一段必然落空，实际取到的是渠道里存的历史收件人 —— 取不到就是空）。
     */
    @PostMapping("/{id}/test")
    @RequiresPermission("alert:write")
    public R<ChannelTestVO> test(@PathVariable Long id,
                                 @RequestBody(required = false) ChannelTestDTO body) {
        return R.ok(notifyChannelService.test(id, body == null ? null : body.getRecipients()));
    }
}
