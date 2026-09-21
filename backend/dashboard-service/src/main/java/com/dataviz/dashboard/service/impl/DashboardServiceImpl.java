package com.dataviz.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.dashboard.dto.DashboardCreateDTO;
import com.dataviz.dashboard.dto.DashboardUpdateDTO;
import com.dataviz.dashboard.entity.Dashboard;
import com.dataviz.dashboard.mapper.DashboardMapper;
import com.dataviz.dashboard.mapper.WidgetMapper;
import com.dataviz.dashboard.service.DashboardService;
import com.dataviz.dashboard.vo.DashboardListVO;
import com.dataviz.dashboard.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;
    private final WidgetMapper widgetMapper;

    @Override
    @Transactional
    public Long create(DashboardCreateDTO dto) {
        Dashboard dashboard = new Dashboard();
        BeanUtils.copyProperties(dto, dashboard);
        dashboard.setStatus(0);
        dashboard.setViewCount(0L);
        dashboard.setLikeCount(0L);
        dashboardMapper.insert(dashboard);
        log.info("Created dashboard: id={}, name={}", dashboard.getId(), dashboard.getName());
        return dashboard.getId();
    }

    @Override
    @Transactional
    public void update(DashboardUpdateDTO dto) {
        Dashboard dashboard = dashboardMapper.selectById(dto.getId());
        if (dashboard == null) {
            throw new RuntimeException("Dashboard not found: " + dto.getId());
        }
        BeanUtils.copyProperties(dto, dashboard);
        dashboardMapper.updateById(dashboard);
        log.info("Updated dashboard: id={}", dashboard.getId());
    }

    @Override
    public DashboardVO getById(Long id) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new RuntimeException("Dashboard not found: " + id);
        }
        dashboardMapper.incrementViewCount(id);
        DashboardVO vo = new DashboardVO();
        BeanUtils.copyProperties(dashboard, vo);
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        dashboardMapper.deleteById(id);
        log.info("Deleted dashboard: id={}", id);
    }

    @Override
    public Page<DashboardListVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<Dashboard> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Dashboard> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Dashboard::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Dashboard::getStatus, status);
        }
        wrapper.orderByDesc(Dashboard::getCreateTime);
        Page<Dashboard> result = dashboardMapper.selectPage(page, wrapper);
        Page<DashboardListVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(d -> {
            DashboardListVO vo = new DashboardListVO();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional
    public void publish(Long id) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new RuntimeException("Dashboard not found: " + id);
        }
        dashboard.setStatus(1);
        dashboardMapper.updateById(dashboard);
        log.info("Published dashboard: id={}", id);
    }

    @Override
    @Transactional
    public void unpublish(Long id) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new RuntimeException("Dashboard not found: " + id);
        }
        dashboard.setStatus(0);
        dashboardMapper.updateById(dashboard);
        log.info("Unpublished dashboard: id={}", id);
    }

    @Override
    @Transactional
    public Long copy(Long id) {
        Dashboard source = dashboardMapper.selectById(id);
        if (source == null) {
            throw new RuntimeException("Dashboard not found: " + id);
        }
        Dashboard copy = new Dashboard();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setName(source.getName() + " (Copy)");
        copy.setStatus(0);
        copy.setViewCount(0L);
        copy.setLikeCount(0L);
        dashboardMapper.insert(copy);
        log.info("Copied dashboard: sourceId={}, newId={}", id, copy.getId());
        return copy.getId();
    }

    @Override
    public List<DashboardListVO> listTemplates() {
        LambdaQueryWrapper<Dashboard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dashboard::getIsTemplate, true)
               .eq(Dashboard::getStatus, 1)
               .orderByDesc(Dashboard::getCreateTime);
        List<Dashboard> list = dashboardMapper.selectList(wrapper);
        return list.stream().map(d -> {
            DashboardListVO vo = new DashboardListVO();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
