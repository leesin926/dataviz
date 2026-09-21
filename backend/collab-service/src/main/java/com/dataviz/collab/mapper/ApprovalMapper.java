package com.dataviz.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.collab.entity.CollabApproval;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalMapper extends BaseMapper<CollabApproval> {
}
