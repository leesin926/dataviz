package com.dataviz.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.datasource.entity.Datasource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源Mapper
 */
@Mapper
public interface DatasourceMapper extends BaseMapper<Datasource> {
}
