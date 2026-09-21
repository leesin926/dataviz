package com.dataviz.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dashboard_widget")
public class DashboardWidget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dashboardId;

    private String widgetType;

    private String title;

    private String configJson;

    private String dataConfigJson;

    private String positionJson;

    private Integer sort;
}
