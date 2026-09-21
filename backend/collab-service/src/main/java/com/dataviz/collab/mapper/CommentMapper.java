package com.dataviz.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.collab.entity.CollabComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<CollabComment> {
}
