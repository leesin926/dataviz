package com.dataviz.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.model.entity.ModelMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标Mapper
 */
@Mapper
public interface MetricMapper extends BaseMapper<ModelMetric> {
}
