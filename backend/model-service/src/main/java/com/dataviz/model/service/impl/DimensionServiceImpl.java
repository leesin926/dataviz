package com.dataviz.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.DimensionCreateDTO;
import com.dataviz.model.entity.ModelDimension;
import com.dataviz.model.mapper.DimensionMapper;
import com.dataviz.model.service.DimensionService;
import com.dataviz.model.vo.DimensionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 维度服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DimensionServiceImpl implements DimensionService {

    private final DimensionMapper dimensionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDimension(DimensionCreateDTO dto, String tenantId) {
        ModelDimension dimension = new ModelDimension();
        BeanUtils.copyProperties(dto, dimension);
        dimension.setTenantId(tenantId);

        dimensionMapper.insert(dimension);
        log.info("Created dimension: {} (id={})", dimension.getName(), dimension.getId());
        return dimension.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDimension(Long id, DimensionCreateDTO dto) {
        ModelDimension dimension = dimensionMapper.selectById(id);
        if (dimension == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "维度不存在");
        }
        BeanUtils.copyProperties(dto, dimension);
        dimensionMapper.updateById(dimension);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDimension(Long id) {
        ModelDimension dimension = dimensionMapper.selectById(id);
        if (dimension == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "维度不存在");
        }
        dimensionMapper.deleteById(id);
    }

    @Override
    public DimensionVO getDimensionById(Long id) {
        ModelDimension dimension = dimensionMapper.selectById(id);
        if (dimension == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "维度不存在");
        }
        DimensionVO vo = new DimensionVO();
        BeanUtils.copyProperties(dimension, vo);
        return vo;
    }

    @Override
    public PageResult<DimensionVO> listDimensions(Long datasourceId, String tenantId, PageQuery pageQuery) {
        Page<ModelDimension> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<ModelDimension> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelDimension::getTenantId, tenantId);
        if (datasourceId != null) {
            wrapper.eq(ModelDimension::getDatasourceId, datasourceId);
        }
        wrapper.orderByDesc(ModelDimension::getCreateTime);

        Page<ModelDimension> result = dimensionMapper.selectPage(page, wrapper);
        List<DimensionVO> voList = result.getRecords().stream().map(dim -> {
            DimensionVO vo = new DimensionVO();
            BeanUtils.copyProperties(dim, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }
}
