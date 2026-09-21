package com.dataviz.model.service;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.model.dto.DatasetCreateDTO;
import com.dataviz.model.vo.DatasetMetaVO;
import com.dataviz.model.vo.DatasetPreviewVO;
import com.dataviz.model.vo.DatasetVO;

/**
 * 数据集服务接口
 */
public interface DatasetService {

    /**
     * 创建数据集
     */
    Long createDataset(DatasetCreateDTO dto, String tenantId);

    /**
     * 更新数据集
     */
    void updateDataset(Long id, DatasetCreateDTO dto);

    /**
     * 删除数据集
     */
    void deleteDataset(Long id);

    /**
     * 获取数据集详情
     */
    DatasetVO getDatasetById(Long id);

    /**
     * 取数据集执行元数据（供 analysis/etl 内部调用）。
     * <p>与 {@link #getDatasetById} 的区别是这里"查不到"是正常结果而不是错误：内部调用方要能
     * 把"记录不存在"和"服务调用失败"分开处理，所以本方法返回 null 而不抛 404。</p>
     *
     * @return null 表示数据集不存在（含已逻辑删除）
     */
    DatasetMetaVO getDatasetMeta(Long id);

    /**
     * 分页查询数据集列表
     */
    PageResult<DatasetVO> listDatasets(String tenantId, PageQuery pageQuery);

    /**
     * 预览数据集
     */
    DatasetPreviewVO previewDataset(Long datasetId, Integer limit);
}
