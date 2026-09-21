package com.dataviz.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.monitor.entity.MonitorMetric;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonitorMetricMapper extends BaseMapper<MonitorMetric> {
}
