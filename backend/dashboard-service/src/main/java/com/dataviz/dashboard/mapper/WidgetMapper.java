package com.dataviz.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.dashboard.entity.DashboardWidget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WidgetMapper extends BaseMapper<DashboardWidget> {
}
