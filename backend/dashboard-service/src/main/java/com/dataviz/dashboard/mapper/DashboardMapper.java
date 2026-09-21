package com.dataviz.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.dashboard.entity.Dashboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DashboardMapper extends BaseMapper<Dashboard> {

    @Update("UPDATE dashboard SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    @Update("UPDATE dashboard SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementLikeCount(@Param("id") Long id);
}
