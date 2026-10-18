package com.dataviz.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.dashboard.dto.WidgetCreateDTO;
import com.dataviz.dashboard.dto.WidgetUpdateDTO;
import com.dataviz.dashboard.entity.DashboardWidget;
import com.dataviz.dashboard.mapper.DashboardMapper;
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
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetServiceImpl implements WidgetService {

    private final WidgetMapper widgetMapper;
    private final DashboardMapper dashboardMapper;

    @Override
    @Transactional
    public Long create(WidgetCreateDTO dto) {
        // dashboard_widget 没有 deleted 列，收下的孤儿行永远清不掉也不会出现在任何列表里，所以父级必须在插入前验存在
        if (dto.getDashboardId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "组件所属仪表盘 id 不能为空");
        }
        if (dashboardMapper.selectById(dto.getDashboardId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "仪表盘不存在: " + dto.getDashboardId());
        }
        DashboardWidget widget = new DashboardWidget();
        BeanUtils.copyProperties(dto, widget);
        widgetMapper.insert(widget);
        log.info("Created widget: id={}, dashboardId={}", widget.getId(), widget.getDashboardId());
        return widget.getId();
    }

    @Override
    @Transactional
    public void update(WidgetUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "组件 id 不能为空");
        }
        DashboardWidget widget = widgetMapper.selectById(dto.getId());
        if (widget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Widget not found: " + dto.getId());
        }
        BeanUtils.copyProperties(dto, widget, "id");
        widgetMapper.updateById(widget);
        log.info("Updated widget: id={}", widget.getId());
    }

    @Override
    public WidgetVO getById(Long id) {
        DashboardWidget widget = widgetMapper.selectById(id);
        if (widget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Widget not found: " + id);
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
