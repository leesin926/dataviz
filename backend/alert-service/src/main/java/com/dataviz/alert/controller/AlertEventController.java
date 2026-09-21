package com.dataviz.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.AlertEventAckDTO;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.mapper.AlertEventMapper;
import com.dataviz.alert.service.AlertRuleService;
import com.dataviz.alert.vo.AlertEventVO;
import com.dataviz.alert.vo.AlertStatsVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/alert/event")
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventMapper alertEventMapper;
    private final AlertRuleService alertRuleService;

    @GetMapping("/page")
    public R<PageResult<AlertEventVO>> page(PageQuery pageQuery,
                                            @RequestParam(required = false) Long ruleId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String severity) {
        Page<AlertEvent> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (ruleId != null) {
            wrapper.eq(AlertEvent::getRuleId, ruleId);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(AlertEvent::getStatus, status);
        }
        if (severity != null && !severity.trim().isEmpty()) {
            wrapper.eq(AlertEvent::getSeverity, severity);
        }
        wrapper.orderByDesc(AlertEvent::getCreateTime);
        Page<AlertEvent> result = alertEventMapper.selectPage(page, wrapper);
        List<AlertEventVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return R.ok(PageResult.of(records, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize()));
    }

    @PostMapping("/acknowledge")
    public R<Void> acknowledge(@RequestBody AlertEventAckDTO dto) {
        AlertEvent event = alertEventMapper.selectById(dto.getId());
        if (event == null) {
            throw new BizException("Alert event not found: " + dto.getId());
        }
        event.setStatus("ACKNOWLEDGED");
        alertEventMapper.updateById(event);
        log.info("Acknowledged alert event: id={}", dto.getId());
        return R.ok();
    }

    @PostMapping("/resolve")
    public R<Void> resolve(@RequestBody AlertEventAckDTO dto) {
        AlertEvent event = alertEventMapper.selectById(dto.getId());
        if (event == null) {
            throw new BizException("Alert event not found: " + dto.getId());
        }
        event.setStatus("RESOLVED");
        event.setResolvedAt(LocalDateTime.now());
        alertEventMapper.updateById(event);
        log.info("Resolved alert event: id={}", dto.getId());
        return R.ok();
    }

    @GetMapping("/stats")
    public R<AlertStatsVO> getStats() {
        long totalRules = alertRuleService.page(new PageQuery(), null, null, null).getTotal();
        long pendingCount = alertEventMapper.selectCount(
                new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getStatus, "PENDING"));
        long acknowledgedCount = alertEventMapper.selectCount(
                new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getStatus, "ACKNOWLEDGED"));
        long resolvedCount = alertEventMapper.selectCount(
                new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getStatus, "RESOLVED"));
        long criticalCount = alertEventMapper.selectCount(
                new LambdaQueryWrapper<AlertEvent>().eq(AlertEvent::getSeverity, "CRITICAL"));
        long totalEvents = pendingCount + acknowledgedCount + resolvedCount;

        AlertStatsVO stats = AlertStatsVO.builder()
                .totalRules(totalRules)
                .enabledRules(0L) // Would need a separate query for enabled count
                .pendingEvents(pendingCount)
                .acknowledgedEvents(acknowledgedCount)
                .resolvedEvents(resolvedCount)
                .totalEvents(totalEvents)
                .criticalEvents(criticalCount)
                .build();
        return R.ok(stats);
    }

    private AlertEventVO toVO(AlertEvent event) {
        AlertEventVO vo = new AlertEventVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
    }
}
