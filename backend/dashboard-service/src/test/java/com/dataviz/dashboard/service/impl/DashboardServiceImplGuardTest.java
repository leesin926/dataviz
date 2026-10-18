package com.dataviz.dashboard.service.impl;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.dashboard.dto.DashboardCreateDTO;
import com.dataviz.dashboard.dto.DashboardUpdateDTO;
import com.dataviz.dashboard.entity.Dashboard;
import com.dataviz.dashboard.mapper.DashboardMapper;
import com.dataviz.dashboard.mapper.WidgetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 创建/更新的入参卫语句：DB 的 NOT NULL 约束不是校验层，撞上去只会得到一句"系统内部错误"。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplGuardTest {

    @Mock
    private DashboardMapper dashboardMapper;

    @Mock
    private WidgetMapper widgetMapper;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("创建 - 名称缺失报 400，且不去插一条注定被约束拒掉的行")
    void create_whenNameMissing_throwsBadRequest(String name) {
        DashboardCreateDTO dto = new DashboardCreateDTO();
        dto.setName(name);

        BizException ex = assertThrows(BizException.class, () -> dashboardService.create(dto));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(dashboardMapper);
    }

    @Test
    @DisplayName("更新 - id 缺失报 400，不会去 selectById(null)")
    void update_whenIdMissing_throwsBadRequest() {
        DashboardUpdateDTO dto = new DashboardUpdateDTO();
        dto.setName("zz_ay20_dash");

        BizException ex = assertThrows(BizException.class, () -> dashboardService.update(dto));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(dashboardMapper);
    }

    @Test
    @DisplayName("更新 - 名称给了空串报 400，不会落库")
    void update_whenNameIsBlankString_throwsBadRequest() {
        DashboardUpdateDTO dto = new DashboardUpdateDTO();
        dto.setId(905L);
        dto.setName("  ");

        BizException ex = assertThrows(BizException.class, () -> dashboardService.update(dto));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(dashboardMapper);
    }

    @Test
    @DisplayName("更新 - 不带 name 仍是局部更新（卫语句不能把这条路堵死）")
    void update_whenNameAbsent_writesThrough() {
        Dashboard stored = new Dashboard();
        stored.setId(905L);
        stored.setName("zz_ay20_dash");
        when(dashboardMapper.selectById(905L)).thenReturn(stored);
        DashboardUpdateDTO dto = new DashboardUpdateDTO();
        dto.setId(905L);
        dto.setDescription("只改描述");

        dashboardService.update(dto);

        verify(dashboardMapper).updateById(stored);
        assertEquals("只改描述", stored.getDescription());
    }
}
