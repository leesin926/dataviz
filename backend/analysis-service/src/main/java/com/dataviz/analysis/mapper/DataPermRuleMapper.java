package com.dataviz.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.analysis.entity.DataPermRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行列权限规则 Mapper
 */
@Mapper
public interface DataPermRuleMapper extends BaseMapper<DataPermRule> {
}
