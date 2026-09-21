package com.dataviz.datasource.service;

import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.datasource.dto.*;
import com.dataviz.datasource.vo.ColumnInfoVO;
import com.dataviz.datasource.vo.DatasourceListVO;
import com.dataviz.datasource.vo.DatasourceVO;
import com.dataviz.datasource.vo.TableInfoVO;

import java.util.List;
import java.util.Map;

/**
 * 数据源服务接口
 */
public interface DatasourceService {

    /**
     * 创建数据源
     */
    Long createDatasource(DatasourceCreateDTO dto, String tenantId);

    /**
     * 更新数据源
     */
    void updateDatasource(Long id, DatasourceUpdateDTO dto);

    /**
     * 删除数据源
     */
    void deleteDatasource(Long id);

    /**
     * 获取数据源详情
     */
    DatasourceVO getDatasourceById(Long id);

    /**
     * 分页查询数据源列表
     */
    PageResult<DatasourceListVO> listDatasources(String name, String type, Integer status,
                                                  String tenantId, PageQuery pageQuery);

    /**
     * 测试数据源连接
     */
    boolean testConnection(Long id);

    /**
     * 测试新连接(未保存)
     */
    boolean testConnection(TestConnectionDTO dto);

    /**
     * 获取数据源中的表列表
     */
    List<TableInfoVO> getTables(Long id);

    /**
     * 获取表的列信息
     */
    List<ColumnInfoVO> getTableColumns(Long id, String tableName);

    /**
     * 执行只读查询
     */
    Map<String, Object> executeQuery(QueryExecuteDTO dto);
}
