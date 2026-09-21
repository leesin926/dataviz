package com.dataviz.model.service;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.DimensionCreateDTO;
import com.dataviz.model.vo.DimensionVO;

/**
 * 维度服务接口
 */
public interface DimensionService {

    /**
     * 创建维度
     */
    Long createDimension(DimensionCreateDTO dto, String tenantId);

    /**
     * 更新维度
     */
    void updateDimension(Long id, DimensionCreateDTO dto);

    /**
     * 删除维度
     */
    void deleteDimension(Long id);

    /**
     * 获取维度详情
     */
    DimensionVO getDimensionById(Long id);

    /**
     * 分页查询维度列表
     */
    PageResult<DimensionVO> listDimensions(Long datasourceId, String tenantId, PageQuery pageQuery);
}
