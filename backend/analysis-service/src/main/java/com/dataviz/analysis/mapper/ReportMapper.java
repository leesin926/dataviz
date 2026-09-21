package com.dataviz.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.analysis.entity.AnalysisReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分析报告Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<AnalysisReport> {
}
