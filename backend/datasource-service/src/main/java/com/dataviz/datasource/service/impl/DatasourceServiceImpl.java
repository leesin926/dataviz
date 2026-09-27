package com.dataviz.datasource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.datasource.dto.*;
import com.dataviz.datasource.entity.Datasource;
import com.dataviz.datasource.entity.DatasourceType;
import com.dataviz.datasource.factory.DataSourceConnectionFactory;
import com.dataviz.datasource.mapper.DatasourceMapper;
import com.dataviz.datasource.service.DatasourceService;
import com.dataviz.datasource.vo.ColumnInfoVO;
import com.dataviz.datasource.vo.DatasourceListVO;
import com.dataviz.datasource.vo.DatasourceVO;
import com.dataviz.datasource.vo.TableInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper datasourceMapper;
    private final DataSourceConnectionFactory connectionFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDatasource(DatasourceCreateDTO dto, String tenantId) {
        // 验证类型
        DatasourceType dsType = DatasourceType.fromCode(dto.getType());

        // 如果是JDBC支持的类型，先测试连接
        if (dsType.isJdbcSupported()) {
            testJdbcConnection(dto.getType(), dto.getConfig());
        }

        Datasource datasource = new Datasource();
        BeanUtils.copyProperties(dto, datasource);
        datasource.setTenantId(tenantId);
        datasource.setStatus(1);

        datasourceMapper.insert(datasource);
        log.info("Created datasource: {} (type={})", datasource.getName(), datasource.getType());
        return datasource.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDatasource(Long id, DatasourceUpdateDTO dto) {
        Datasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, datasource);
        datasourceMapper.updateById(datasource);
        // 重新创建连接池
        connectionFactory.removePool(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDatasource(Long id) {
        connectionFactory.removePool(id);
        datasourceMapper.deleteById(id);
    }

    @Override
    public DatasourceVO getDatasourceById(Long id) {
        Datasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        DatasourceVO vo = new DatasourceVO();
        BeanUtils.copyProperties(datasource, vo);
        return vo;
    }

    @Override
    public PageResult<DatasourceListVO> listDatasources(String name, String type, Integer status,
                                                         String tenantId, PageQuery pageQuery) {
        Page<Datasource> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Datasource::getTenantId, tenantId);
        if (StringUtils.hasText(name)) {
            wrapper.like(Datasource::getName, name);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Datasource::getType, type);
        }
        if (status != null) {
            wrapper.eq(Datasource::getStatus, status);
        }
        wrapper.orderByDesc(Datasource::getCreateTime);

        Page<Datasource> result = datasourceMapper.selectPage(page, wrapper);
        List<DatasourceListVO> voList = result.getRecords().stream().map(ds -> {
            DatasourceListVO vo = new DatasourceListVO();
            vo.setId(ds.getId());
            vo.setName(ds.getName());
            vo.setType(ds.getType());
            vo.setStatus(ds.getStatus());
            vo.setDescription(ds.getDescription());
            vo.setCreateTime(ds.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public boolean testConnection(Long id) {
        Datasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        DatasourceType dsType = DatasourceType.fromCode(datasource.getType());
        if (!dsType.isJdbcSupported()) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "非JDBC数据源类型不支持连接测试: " + dsType.getCode());
        }
        try (Connection conn = connectionFactory.getConnection(id, datasource.getType(), datasource.getConfig())) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("Connection test failed for datasource: {}", id, e);
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "连接测试失败: " + e.getMessage());
        }
    }

    @Override
    public boolean testConnection(TestConnectionDTO dto) {
        DatasourceType dsType = DatasourceType.fromCode(dto.getType());
        if (!dsType.isJdbcSupported()) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "非JDBC数据源类型不支持连接测试: " + dsType.getCode());
        }
        try (Connection conn = connectionFactory.getTestConnection(dto.getType(), dto.getConfig())) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("Connection test failed", e);
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "连接测试失败: " + e.getMessage());
        }
    }

    @Override
    public List<TableInfoVO> getTables(Long id) {
        Datasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        DatasourceType dsType = DatasourceType.fromCode(datasource.getType());
        if (!dsType.isJdbcSupported()) {
            throw new BizException("非JDBC数据源不支持获取表列表");
        }

        List<TableInfoVO> tables = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection(id, datasource.getType(), datasource.getConfig())) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = null;
            if (dsType == DatasourceType.POSTGRESQL) {
                schema = "public";
                catalog = null;
            }

            try (ResultSet rs = dbMeta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    TableInfoVO tableInfo = new TableInfoVO();
                    tableInfo.setTableName(rs.getString("TABLE_NAME"));
                    tableInfo.setTableComment(rs.getString("REMARKS"));
                    tableInfo.setTableType(rs.getString("TABLE_TYPE"));
                    tables.add(tableInfo);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get tables for datasource: {}", id, e);
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "获取表列表失败: " + e.getMessage());
        }
        return tables;
    }

    @Override
    public List<ColumnInfoVO> getTableColumns(Long id, String tableName) {
        Datasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        DatasourceType dsType = DatasourceType.fromCode(datasource.getType());
        if (!dsType.isJdbcSupported()) {
            throw new BizException("非JDBC数据源不支持获取列信息");
        }

        List<ColumnInfoVO> columns = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection(id, datasource.getType(), datasource.getConfig())) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = null;
            if (dsType == DatasourceType.POSTGRESQL) {
                schema = "public";
                catalog = null;
            }

            // 获取主键列
            Set<String> pkColumns = new HashSet<>();
            try (ResultSet pkRs = dbMeta.getPrimaryKeys(catalog, schema, tableName)) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // 获取列信息
            try (ResultSet rs = dbMeta.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    ColumnInfoVO col = new ColumnInfoVO();
                    col.setColumnName(rs.getString("COLUMN_NAME"));
                    col.setDataType(rs.getString("TYPE_NAME"));
                    col.setColumnSize(rs.getInt("COLUMN_SIZE"));
                    col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.setDefaultValue(rs.getString("COLUMN_DEF"));
                    col.setComment(rs.getString("REMARKS"));
                    col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                    columns.add(col);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get columns for datasource: {}, table: {}", id, tableName, e);
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "获取列信息失败: " + e.getMessage());
        }
        return columns;
    }

    @Override
    public Map<String, Object> executeQuery(QueryExecuteDTO dto) {
        Datasource datasource = datasourceMapper.selectById(dto.getDatasourceId());
        if (datasource == null) {
            throw new BizException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        DatasourceType dsType = DatasourceType.fromCode(datasource.getType());
        if (!dsType.isJdbcSupported()) {
            throw new BizException("非JDBC数据源不支持SQL查询");
        }

        // 安全检查: 只允许SELECT语句
        String sql = dto.getSql().trim();
        String upperSql = sql.toUpperCase();
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("SHOW") && !upperSql.startsWith("DESCRIBE")) {
            throw new BizException(ErrorCode.SQL_SYNTAX_ERROR, "只允许执行只读查询语句 (SELECT/SHOW/DESCRIBE)");
        }

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try (Connection conn = connectionFactory.getConnection(
                dto.getDatasourceId(), datasource.getType(), datasource.getConfig());
             Statement stmt = conn.createStatement()) {

            // 设置超时和行数限制
            int timeout = dto.getTimeout() != null ? dto.getTimeout() : 30;
            int maxRows = dto.getMaxRows() != null ? dto.getMaxRows() : 1000;
            stmt.setQueryTimeout(timeout);
            // 多取一行来判断"是否还有数据"：rowcount 等于上限既可能是被截断，也可能恰好那么多，
            // 只有读到第 maxRows+1 行才能确定前面那些不是全量。
            stmt.setMaxRows(maxRows + 1);
            boolean truncated = false;

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 获取列名
                for (int i = 1; i <= columnCount; i++) {
                    columnNames.add(metaData.getColumnLabel(i));
                }

                // 获取数据行
                while (rs.next()) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(columnNames.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            result.put("columns", columnNames);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("truncated", truncated);
            result.put("executionTime", executionTime);
            result.put("sql", sql);

        } catch (BizException e) {
            throw e;
        } catch (SQLTimeoutException e) {
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "查询超时");
        } catch (SQLException e) {
            log.error("SQL execution failed: {}", sql, e);
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "SQL执行失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Query execution failed: {}", sql, e);
            throw new BizException(ErrorCode.SQL_EXECUTION_ERROR, "查询执行失败: " + e.getMessage());
        }

        return result;
    }

    private void testJdbcConnection(String type, String config) {
        try (Connection conn = connectionFactory.getTestConnection(type, config)) {
            if (!conn.isValid(5)) {
                throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED, "无法建立有效连接");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "连接失败: " + e.getMessage());
        }
    }
}
