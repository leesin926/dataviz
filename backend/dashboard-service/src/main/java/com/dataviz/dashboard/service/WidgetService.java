package com.dataviz.dashboard.service;

import com.dataviz.dashboard.dto.WidgetCreateDTO;
import com.dataviz.dashboard.vo.WidgetVO;

import java.util.List;

public interface WidgetService {

    Long create(WidgetCreateDTO dto);

    void update(WidgetCreateDTO dto);

    WidgetVO getById(Long id);

    void delete(Long id);

    List<WidgetVO> listByDashboardId(Long dashboardId);
}
