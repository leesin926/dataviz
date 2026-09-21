package com.dataviz.screen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.screen.entity.Screen;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScreenMapper extends BaseMapper<Screen> {

    @Update("UPDATE screen SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(@Param("id") Long id);
}
