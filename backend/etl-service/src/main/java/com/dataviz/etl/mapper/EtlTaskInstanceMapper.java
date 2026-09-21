package com.dataviz.etl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.etl.entity.EtlTaskInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EtlTaskInstanceMapper extends BaseMapper<EtlTaskInstance> {
}
