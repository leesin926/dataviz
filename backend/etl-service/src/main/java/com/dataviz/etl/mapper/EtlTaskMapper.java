package com.dataviz.etl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.etl.entity.EtlTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * ETL任务Mapper
 */
@Mapper
public interface EtlTaskMapper extends BaseMapper<EtlTask> {
}
