package com.dataviz.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.schedule.entity.ScheduleJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleJobMapper extends BaseMapper<ScheduleJob> {
}
