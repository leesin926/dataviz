package com.dataviz.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.alert.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    @Select("SELECT COUNT(*) FROM alert_event WHERE tenant_id = #{tenantId} AND status = #{status}")
    long countByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);
}
