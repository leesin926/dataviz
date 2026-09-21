package com.dataviz.dashboard.dto;

import lombok.Data;

@Data
public class WidgetCreateDTO {

    private Long dashboardId;

    private String widgetType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer sort;
}
