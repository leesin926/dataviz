package com.dataviz.dashboard.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WidgetVO {

    private Long id;

    private Long dashboardId;

    private String widgetType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer sort;
}
