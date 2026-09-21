package com.dataviz.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.dashboard.dto.WidgetCreateDTO;
import com.dataviz.dashboard.entity.DashboardWidget;
import com.dataviz.dashboard.mapper.WidgetMapper;
import com.dataviz.dashboard.service.WidgetService;
import com.dataviz.dashboard.vo.WidgetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetServiceImpl implements WidgetService {

    private final WidgetMapper widgetMapper;

    @Override
    @Transactional
    public Long create(WidgetCreateDTO dto) {
        DashboardWidget widget = new DashboardWidget();
        BeanUtils.copyProperties(dto, widget);
        widgetMapper.insert(widget);
        log.info("Created widget: id={}, dashboardId={}", widget.getId(), widget.getDashboardId());
        return widget.getId();
    }

    @Override
    @Transactional
    public void update(WidgetCreateDTO dto) {
        DashboardWidget widget = widgetMapper.selectById(dto.getDashboardId());
        if (widget == null) {
            throw new RuntimeException("Widget not found");
        }
        BeanUtils.copyProperties(dto, widget);
        widgetMapper.updateById(widget);
        log.info("Updated widget: id={}", widget.getId());
    }

    @Override
    public WidgetVO getById(Long id) {
        DashboardWidget widget = widgetMapper.selectById(id);
        if (widget == null) {
            throw new RuntimeException("Widget not found: " + id);
        }
        WidgetVO vo = new WidgetVO();
        BeanUtils.copyProperties(widget, vo);
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        widgetMapper.deleteById(id);
        log.info("Deleted widget: id={}", id);
    }

    @Override
    public List<WidgetVO> listByDashboardId(Long dashboardId) {
        LambdaQueryWrapper<DashboardWidget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DashboardWidget::getDashboardId, dashboardId)
               .orderByAsc(DashboardWidget::getSort);
        List<DashboardWidget> list = widgetMapper.selectList(wrapper);
        return list.stream().map(w -> {
            WidgetVO vo = new WidgetVO();
            BeanUtils.copyProperties(w, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
