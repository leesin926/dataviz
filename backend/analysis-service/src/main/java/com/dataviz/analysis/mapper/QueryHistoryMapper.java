package com.dataviz.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.analysis.entity.QueryHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 查询历史Mapper
 */
@Mapper
public interface QueryHistoryMapper extends BaseMapper<QueryHistory> {
}
