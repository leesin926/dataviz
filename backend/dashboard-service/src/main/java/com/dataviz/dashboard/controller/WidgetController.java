package com.dataviz.dashboard.controller;

import com.dataviz.dashboard.dto.WidgetCreateDTO;
import com.dataviz.dashboard.service.WidgetService;
import com.dataviz.dashboard.vo.WidgetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/widget")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;

    @PostMapping
    public Long create(@RequestBody WidgetCreateDTO dto) {
        return widgetService.create(dto);
    }

    @PutMapping
    public void update(@RequestBody WidgetCreateDTO dto) {
        widgetService.update(dto);
    }

    @GetMapping("/{id}")
    public WidgetVO getById(@PathVariable Long id) {
        return widgetService.getById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        widgetService.delete(id);
    }

    @GetMapping("/list/{dashboardId}")
    public List<WidgetVO> listByDashboardId(@PathVariable Long dashboardId) {
        return widgetService.listByDashboardId(dashboardId);
    }
}
