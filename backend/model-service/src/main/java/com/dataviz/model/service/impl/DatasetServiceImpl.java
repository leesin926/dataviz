package com.dataviz.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.DatasetCreateDTO;
import com.dataviz.model.entity.ModelDataset;
import com.dataviz.model.mapper.DatasetMapper;
import com.dataviz.model.service.DatasetService;
import com.dataviz.model.vo.DatasetMetaVO;
import com.dataviz.model.vo.DatasetPreviewVO;
import com.dataviz.model.vo.DatasetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据集服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetServiceImpl implements DatasetService {

    /** 预览默认行数沿用原来的 100，避免一次预览把 1000 行灌进浏览器 */
    private static final int PREVIEW_DEFAULT_ROWS = 100;
    private static final int PREVIEW_MAX_ROWS = 1000;

    private static final Pattern TABLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final DatasetMapper datasetMapper;
    private final DatasourceQueryClient datasourceQueryClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataset(DatasetCreateDTO dto, String tenantId) {
        ModelDataset dataset = new ModelDataset();
        BeanUtils.copyProperties(dto, dataset);
        dataset.setTenantId(tenantId);

        datasetMapper.insert(dataset);
        log.info("Created dataset: {} (id={})", dataset.getName(), dataset.getId());
        return dataset.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataset(Long id, DatasetCreateDTO dto) {
        ModelDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在");
        }
        BeanUtils.copyProperties(dto, dataset);
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        ModelDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在");
        }
        datasetMapper.deleteById(id);
    }

    @Override
    public DatasetVO getDatasetById(Long id) {
        ModelDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在");
        }
        DatasetVO vo = new DatasetVO();
        BeanUtils.copyProperties(dataset, vo);
        return vo;
    }

    @Override
    public DatasetMetaVO getDatasetMeta(Long id) {
        ModelDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            return null;
        }
        DatasetMetaVO meta = new DatasetMetaVO();
        BeanUtils.copyProperties(dataset, meta);
        meta.setDatasetId(dataset.getId());
        return meta;
    }

    @Override
    public PageResult<DatasetVO> listDatasets(String tenantId, PageQuery pageQuery) {
        Page<ModelDataset> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<ModelDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelDataset::getTenantId, tenantId);
        wrapper.orderByDesc(ModelDataset::getCreateTime);

        Page<ModelDataset> result = datasetMapper.selectPage(page, wrapper);
        List<DatasetVO> voList = result.getRecords().stream().map(ds -> {
            DatasetVO vo = new DatasetVO();
            BeanUtils.copyProperties(ds, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public DatasetPreviewVO previewDataset(Long datasetId, Integer limit) {
        ModelDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据集不存在");
        }
        if (dataset.getDatasourceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据集未绑定数据源，无法预览");
        }

        int maxRows = limit != null && limit > 0 ? Math.min(limit, PREVIEW_MAX_ROWS) : PREVIEW_DEFAULT_ROWS;
        String sql = buildPreviewSql(dataset, maxRows);

        DatasetPreviewVO previewVO = new DatasetPreviewVO();
        previewVO.setSql(sql);
        // 按 dataset 绑定的数据源执行：本服务自己的连接是 db_model，用它会串库（demo 表在两个库里同名同行数，串了也看不出来）
        DatasourceQueryResult data = datasourceQueryClient.query(dataset.getDatasourceId(), sql, maxRows, null);
        previewVO.setColumns(data.getColumns() == null ? Collections.<String>emptyList() : data.getColumns());
        previewVO.setRows(data.getRows() == null ? Collections.<Map<String, Object>>emptyList() : data.getRows());
        previewVO.setRowCount(previewVO.getRows().size());
        return previewVO;
    }

    /**
     * 预览 SQL：物理表按标识符白名单校验，自定义 SQL 只接受单条只读语句。
     */
    private String buildPreviewSql(ModelDataset dataset, int maxRows) {
        String sqlQuery = dataset.getSqlQuery() == null ? "" : dataset.getSqlQuery().trim();
        if (!sqlQuery.isEmpty()) {
            String single = sqlQuery.replaceAll(";\\s*$", "").trim();
            if (single.contains(";")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 含多条语句，拒绝预览");
            }
            if (!single.toUpperCase().startsWith("SELECT")) {
                throw new BizException(ErrorCode.BAD_REQUEST, "数据集自定义 SQL 不是只读查询，拒绝预览");
            }
            return single + " LIMIT " + maxRows;
        }
        String tableName = dataset.getTableName() == null ? "" : dataset.getTableName().trim();
        if (tableName.isEmpty()) {
            throw new BizException("数据集未配置查询SQL或表名");
        }
        if (!TABLE_IDENTIFIER.matcher(tableName).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据集表名不是合法标识符: " + tableName);
        }
        return "SELECT * FROM `" + tableName + "` LIMIT " + maxRows;
    }
}
