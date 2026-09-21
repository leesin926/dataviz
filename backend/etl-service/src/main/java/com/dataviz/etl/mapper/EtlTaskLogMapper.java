package com.dataviz.etl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.etl.entity.EtlTaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ETL任务日志Mapper
 */
@Mapper
public interface EtlTaskLogMapper extends BaseMapper<EtlTaskLog> {
}
