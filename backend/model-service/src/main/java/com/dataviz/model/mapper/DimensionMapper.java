package com.dataviz.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.model.entity.ModelDimension;
import org.apache.ibatis.annotations.Mapper;

/**
 * 维度Mapper
 */
@Mapper
public interface DimensionMapper extends BaseMapper<ModelDimension> {
}
