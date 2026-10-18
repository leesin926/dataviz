package com.dataviz.dashboard.dto;

import lombok.Data;

/** 刻意不接受 dashboardId：更新只改组件自身，不接受"把组件挪到别的仪表盘"，见 API-100。 */
@Data
public class WidgetUpdateDTO {

    private Long id;

    private String widgetType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer sort;
}
