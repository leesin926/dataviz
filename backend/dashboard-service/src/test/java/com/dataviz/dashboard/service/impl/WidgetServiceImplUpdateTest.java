package com.dataviz.dashboard.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.dashboard.dto.WidgetUpdateDTO;
import com.dataviz.dashboard.entity.DashboardWidget;
import com.dataviz.dashboard.mapper.DashboardMapper;
import com.dataviz.dashboard.mapper.WidgetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 组件更新按自己的主键取行，永远改不到父级 —— API-100 的回归锁。
 */
@ExtendWith(MockitoExtension.class)
class WidgetServiceImplUpdateTest {

    @Mock
    private WidgetMapper widgetMapper;

    @Mock
    private DashboardMapper dashboardMapper;

    @InjectMocks
    private WidgetServiceImpl widgetService;

    private static DashboardWidget storedWidget() {
        DashboardWidget widget = new DashboardWidget();
        widget.setId(907L);
        widget.setDashboardId(905L);
        widget.setWidgetType("bar");
        widget.setTitle("zz_ay20_widget");
        widget.setSort(1);
        return widget;
    }

    @Test
    @DisplayName("更新 DTO 上没有 dashboardId —— 更新不接受挪动组件")
    void updateDto_hasNoDashboardIdField() {
        assertTrue(Arrays.stream(WidgetUpdateDTO.class.getDeclaredFields())
                        .map(Field::getName)
                        .noneMatch("dashboardId"::equals),
                "PUT /api/dashboard/widget 一旦接受 dashboardId，就等于允许把组件挪到别的仪表盘");
    }

    @Test
    @DisplayName("更新 - 按组件主键取行并保留父级")
    void update_whenWidgetExists_keysOnWidgetIdAndKeepsParent() {
        when(widgetMapper.selectById(907L)).thenReturn(storedWidget());
        WidgetUpdateDTO dto = new WidgetUpdateDTO();
        dto.setId(907L);
        dto.setTitle("zz_ay20_widget-renamed");

        widgetService.update(dto);

        verify(widgetMapper).selectById(907L);
        ArgumentCaptor<DashboardWidget> captor = ArgumentCaptor.forClass(DashboardWidget.class);
        verify(widgetMapper).updateById(captor.capture());
        DashboardWidget written = captor.getValue();
        assertEquals(907L, written.getId());
        assertEquals(905L, written.getDashboardId());
        assertEquals("zz_ay20_widget-renamed", written.getTitle());
    }

    @Test
    @DisplayName("更新 - id 缺失时明确报 400 而不是去查别人的行")
    void update_whenIdMissing_throwsBadRequest() {
        WidgetUpdateDTO dto = new WidgetUpdateDTO();
        dto.setTitle("whatever");

        BizException ex = assertThrows(BizException.class, () -> widgetService.update(dto));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(widgetMapper);
    }

    @Test
    @DisplayName("更新 - 组件不存在时报 404")
    void update_whenWidgetMissing_throwsNotFound() {
        when(widgetMapper.selectById(999L)).thenReturn(null);
        WidgetUpdateDTO dto = new WidgetUpdateDTO();
        dto.setId(999L);

        BizException ex = assertThrows(BizException.class, () -> widgetService.update(dto));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(widgetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新 - configJson 传空白串时报 400，一行都不写")
    void update_whenConfigJsonBlank_throwsBadRequestAndWritesNothing() {
        when(widgetMapper.selectById(907L)).thenReturn(storedWidget());
        WidgetUpdateDTO dto = new WidgetUpdateDTO();
        dto.setId(907L);
        dto.setConfigJson("  ");

        BizException ex = assertThrows(BizException.class, () -> widgetService.update(dto));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("configJson 不能为空", ex.getMessage());
        verify(widgetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新 - configJson 为 null 是「不改动」，不该被空白校验拦下")
    void update_whenConfigJsonNull_stillWrites() {
        when(widgetMapper.selectById(907L)).thenReturn(storedWidget());
        WidgetUpdateDTO dto = new WidgetUpdateDTO();
        dto.setId(907L);
        dto.setTitle("zz_ay20_widget-renamed");

        widgetService.update(dto);

        verify(widgetMapper).updateById(any());
    }
}
