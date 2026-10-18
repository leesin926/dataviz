package com.dataviz.dashboard.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.dashboard.dto.WidgetCreateDTO;
import com.dataviz.dashboard.entity.Dashboard;
import com.dataviz.dashboard.entity.DashboardWidget;
import com.dataviz.dashboard.mapper.DashboardMapper;
import com.dataviz.dashboard.mapper.WidgetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 组件创建必须挂在真实存在的仪表盘下 —— API-102 的回归锁。
 * dashboard_widget 没有 deleted 列，放进来的孤儿行既清不掉也不会出现在任何列表里。
 */
@ExtendWith(MockitoExtension.class)
class WidgetServiceImplCreateTest {

    @Mock
    private WidgetMapper widgetMapper;

    @Mock
    private DashboardMapper dashboardMapper;

    @InjectMocks
    private WidgetServiceImpl widgetService;

    private static WidgetCreateDTO dto(Long dashboardId) {
        WidgetCreateDTO dto = new WidgetCreateDTO();
        dto.setDashboardId(dashboardId);
        dto.setWidgetType("bar");
        dto.setTitle("zz_ay21_widget");
        dto.setConfigJson("{}");
        dto.setDataConfigJson("{}");
        dto.setPositionJson("{}");
        dto.setSort(1);
        return dto;
    }

    @Test
    @DisplayName("创建 - 父仪表盘存在才落库")
    void create_whenParentExists_inserts() {
        when(dashboardMapper.selectById(905L)).thenReturn(new Dashboard());
        when(widgetMapper.insert(any(DashboardWidget.class))).thenReturn(1);

        widgetService.create(dto(905L));

        verify(widgetMapper).insert(any(DashboardWidget.class));
    }

    @Test
    @DisplayName("创建 - 父仪表盘不存在时报 404，一行都不写")
    void create_whenParentMissing_throwsNotFoundAndWritesNothing() {
        when(dashboardMapper.selectById(999999999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> widgetService.create(dto(999999999L)));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(widgetMapper, never()).insert(any(DashboardWidget.class));
    }

    @Test
    @DisplayName("创建 - 不带 dashboardId 时报 400，连父级查询都不发")
    void create_whenDashboardIdMissing_throwsBadRequest() {
        BizException ex = assertThrows(BizException.class, () -> widgetService.create(dto(null)));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(widgetMapper);
        verifyNoInteractions(dashboardMapper);
    }
}
