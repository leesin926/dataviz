package com.dataviz.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.model.entity.ModelDataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集Mapper
 */
@Mapper
public interface DatasetMapper extends BaseMapper<ModelDataset> {
}
