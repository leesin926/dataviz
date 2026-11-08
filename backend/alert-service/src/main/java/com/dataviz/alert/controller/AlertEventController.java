package com.dataviz.alert.controller;

import com.dataviz.alert.dto.AlertEventAckDTO;
import com.dataviz.alert.service.AlertEventService;
import com.dataviz.alert.service.AlertRuleService;
import com.dataviz.alert.vo.AlertEventVO;
import com.dataviz.alert.vo.AlertStatsVO;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/alert/event")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 alert:read（PermissionInterceptor 会回落到类上的注解），确认/解决事件等改状态的动作抬到 alert:write。
@RequiresPermission("alert:read")
public class AlertEventController {

    private final AlertEventService alertEventService;
    private final AlertRuleService alertRuleService;

    /**
     * 分页只负责把参数递下去：<b>SQL 与分片绑定都在 {@link AlertEventService} 里</b>，
     * 控制器不碰 mapper（分片打开后每一处 SQL 都得带路由键，收到一处才 review 得住）。
     */
    @GetMapping("/page")
    public R<PageResult<AlertEventVO>> page(PageQuery pageQuery,
                                            @RequestParam(required = false) Long ruleId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String severity) {
        return R.ok(alertEventService.page(ruleId, status, severity,
                pageQuery.getPageNum(), pageQuery.getPageSize()));
    }

    @PostMapping("/acknowledge")
    @RequiresPermission("alert:write")
    public R<Void> acknowledge(@RequestBody AlertEventAckDTO dto) {
        alertEventService.markStatus(dto.getId(), "ACKNOWLEDGED", false);
        log.info("Acknowledged alert event: id={}", dto.getId());
        return R.ok();
    }

    @PostMapping("/resolve")
    @RequiresPermission("alert:write")
    public R<Void> resolve(@RequestBody AlertEventAckDTO dto) {
        alertEventService.markStatus(dto.getId(), "RESOLVED", true);
        log.info("Resolved alert event: id={}", dto.getId());
        return R.ok();
    }

    @GetMapping("/stats")
    public R<AlertStatsVO> getStats() {
        long totalRules = alertRuleService.page(new PageQuery(), null, null, null).getTotal();
        AlertEventService.EventCounts counts = alertEventService.counts();
        long totalEvents = counts.getPending() + counts.getAcknowledged() + counts.getResolved();

        AlertStatsVO stats = AlertStatsVO.builder()
                .totalRules(totalRules)
                .enabledRules(alertRuleService.countEnabled())
                .pendingEvents(counts.getPending())
                .acknowledgedEvents(counts.getAcknowledged())
                .resolvedEvents(counts.getResolved())
                .totalEvents(totalEvents)
                .criticalEvents(counts.getCritical())
                // 分片打开时这四个数都只是服务端窗口内的数；不标出来就会被读成"建库以来一共这些"
                .partial(counts.isPartial())
                .build();
        return R.ok(stats);
    }
}
