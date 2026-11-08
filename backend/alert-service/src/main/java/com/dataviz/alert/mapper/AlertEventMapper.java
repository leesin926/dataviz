package com.dataviz.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataviz.alert.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code alert_event} 的 mapper。
 *
 * <p>这里原本还有一条 {@code @Select("SELECT COUNT(*) FROM alert_event WHERE tenant_id = … AND status = …")}
 * 的 {@code countByStatus}，全仓（main 与 test）零调用方 —— 删掉。留着的代价不是"多一个方法"：
 * 它把<b>逻辑表名</b>硬编码在 SQL 文本里，分片打开后 {@code DynamicTableNameInnerInterceptor} 只改写
 * 它认得的表名，而这条路既没有 {@code ShardContext} 绑定、也不带月份后缀，结果是"查了一张没人写的基表"，
 * 数字永远偏小。与 PI5 那条 {@code getClientCallCount} 同一处理：<b>死方法删掉，不给它加分片。</b>
 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {
}
