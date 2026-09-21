package com.dataviz.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.collab.entity.CollabSubscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubscriptionMapper extends BaseMapper<CollabSubscription> {
}
