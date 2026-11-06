package com.dataviz.model.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.model.dto.DatasetCreateDTO;
import com.dataviz.model.entity.ModelDataset;
import com.dataviz.model.mapper.DatasetMapper;
import com.dataviz.model.vo.DatasetMetaVO;
import com.dataviz.model.vo.DatasetPreviewVO;
import com.dataviz.model.vo.DatasetVO;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DatasetServiceImpl 的决策密集路径：创建/更新的落库语义、软删除、内部元数据，
 * 以及 preview 的 SQL 形状校验（物理表标识符白名单 + 自定义 SQL 的单语句只读规则）。
 * <p>
 * 预览守卫刻意与 analysis 的 SqlBuilder#resolveFrom 对照：两处白名单正则、去尾分号、
 * "必须是 SELECT 开头"完全同源；分叉只在成品形状（预览把 LIMIT 直接接在裸 SQL 后，
 * analysis 把自定义 SQL 包成 {@code (...) t_source} 子查询）与"表名/SQL 都没配"时的错误码
 * （预览用默认 500 的 BizException，analysis 用 400）。这些分叉在对应断言里钉住。
 */
class DatasetServiceImplTest {

    private DatasetMapper datasetMapper;
    private DatasourceQueryClient queryClient;
    private DatasetServiceImpl datasetService;

    @BeforeEach
    void setUp() {
        datasetMapper = mock(DatasetMapper.class);
        queryClient = mock(DatasourceQueryClient.class);
        datasetService = new DatasetServiceImpl(datasetMapper, queryClient);
    }

    private ModelDataset dataset(Long id, Long datasourceId, String tableName, String sqlQuery) {
        ModelDataset ds = new ModelDataset();
        ds.setId(id);
        ds.setTenantId("t-1");
        ds.setName("销售明细");
        ds.setDatasourceId(datasourceId);
        ds.setTableName(tableName);
        ds.setSqlQuery(sqlQuery);
        return ds;
    }

    private DatasourceQueryResult result() {
        DatasourceQueryResult r = new DatasourceQueryResult();
        r.setColumns(Arrays.asList("id", "amount"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("amount", 99);
        r.setRows(Collections.singletonList(row));
        r.setRowCount(1);
        return r;
    }

    // ==================== createDataset ====================

    @Test
    @DisplayName("创建数据集：逐字段拷贝后补租户，回填的 id 即返回值")
    void should_copyFieldsAndSetTenant_when_createDataset() {
        DatasetCreateDTO dto = new DatasetCreateDTO();
        dto.setName("销售明细");
        dto.setDatasourceId(7L);
        dto.setTableName("dwd_sales_daily");
        dto.setSqlQuery("SELECT 1");
        // insert 由 mock 承接，回填自增主键模拟 MP 行为
        when(datasetMapper.insert(any(ModelDataset.class))).thenAnswer(inv -> {
            inv.<ModelDataset>getArgument(0).setId(55L);
            return 1;
        });

        // Given 目标租户为 t-9，与 DTO 里不存在的 tenantId 字段无关
        Long id = datasetService.createDataset(dto, "t-9");

        // Then 返回自增 id，落库实体带目标租户与逐字段值
        assertThat(id).isEqualTo(55L);
        ArgumentCaptor<ModelDataset> captor = ArgumentCaptor.forClass(ModelDataset.class);
        verify(datasetMapper).insert(captor.capture());
        ModelDataset saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("t-9");
        assertThat(saved.getName()).isEqualTo("销售明细");
        assertThat(saved.getDatasourceId()).isEqualTo(7L);
        assertThat(saved.getTableName()).isEqualTo("dwd_sales_daily");
        assertThat(saved.getSqlQuery()).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("创建数据集：不在写入口校验表名/SQL（校验推迟到预览/查询），钉住实际行为")
    void should_storeVerbatim_when_createDatasetCarriesHostileIdentifiers() {
        DatasetCreateDTO dto = new DatasetCreateDTO();
        dto.setName("危险");
        dto.setDatasourceId(7L);
        dto.setTableName("sales; DROP TABLE x");
        dto.setSqlQuery("DELETE FROM t");

        // When 创建阶段没有标识符白名单，也没有单语句/只读校验
        datasetService.createDataset(dto, "t-9");

        // Then 原样入库（真正的拒绝发生在 previewDataset / analysis 侧，见对应用例）
        ArgumentCaptor<ModelDataset> captor = ArgumentCaptor.forClass(ModelDataset.class);
        verify(datasetMapper).insert(captor.capture());
        assertThat(captor.getValue().getTableName()).isEqualTo("sales; DROP TABLE x");
        assertThat(captor.getValue().getSqlQuery()).isEqualTo("DELETE FROM t");
    }

    // ==================== updateDataset ====================

    @Test
    @DisplayName("更新数据集：查不到即 404 语义，不静默插入")
    void should_throwNotFound_when_updateDatasetMissing() {
        when(datasetMapper.selectById(404L)).thenReturn(null);

        // Given/When 一个不存在的 id
        assertThatThrownBy(() -> datasetService.updateDataset(404L, new DatasetCreateDTO()))
                // Then 抛 NOT_FOUND(404) 且文案精确
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在");
        verify(datasetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("更新数据集：DTO 里没有的列被 copyProperties 抹成 null（清空语义）")
    void should_clearFieldsToNull_when_updateDatasetDtoOmitsThem() {
        ModelDataset existing = dataset(10L, 7L, "old_table", "SELECT old");
        when(datasetMapper.selectById(10L)).thenReturn(existing);
        DatasetCreateDTO dto = new DatasetCreateDTO();
        dto.setName("改名后");
        dto.setDatasourceId(8L);
        dto.setTableName("new_table");
        // sqlQuery 不传 → null

        datasetService.updateDataset(10L, dto);

        ArgumentCaptor<ModelDataset> captor = ArgumentCaptor.forClass(ModelDataset.class);
        verify(datasetMapper).updateById(captor.capture());
        ModelDataset saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("改名后");
        assertThat(saved.getDatasourceId()).isEqualTo(8L);
        assertThat(saved.getTableName()).isEqualTo("new_table");
        // 关键：与 user-service.updateUser 同一条 copyProperties 语义——DTO 传 null 就抹掉原值
        assertThat(saved.getSqlQuery()).isNull();
        assertThat(saved.getId()).isEqualTo(10L);
    }

    // ==================== deleteDataset ====================

    @Test
    @DisplayName("删除数据集：先确认存在再按 id 逻辑删除（@TableLogic 由 MP 侧执行）")
    void should_deleteById_when_deleteDatasetFound() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "t", null));

        datasetService.deleteDataset(10L);

        verify(datasetMapper).deleteById(10L);
    }

    @Test
    @DisplayName("删除数据集：不存在时抛 404，不调用 deleteById")
    void should_throwNotFound_when_deleteDatasetMissing() {
        when(datasetMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> datasetService.deleteDataset(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在");
        verify(datasetMapper, never()).deleteById(any());
    }

    // ==================== getDatasetById / getDatasetMeta ====================

    @Test
    @DisplayName("详情：查不到抛 404")
    void should_throw_when_getDatasetByIdMissing() {
        when(datasetMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> datasetService.getDatasetById(404L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在");
    }

    @Test
    @DisplayName("详情：查到即拷成 VO，字段一一对应")
    void should_copyToVO_when_getDatasetByIdFound() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "dwd_x", "SELECT 1"));

        DatasetVO vo = datasetService.getDatasetById(10L);

        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getTenantId()).isEqualTo("t-1");
        assertThat(vo.getName()).isEqualTo("销售明细");
        assertThat(vo.getDatasourceId()).isEqualTo(7L);
        assertThat(vo.getTableName()).isEqualTo("dwd_x");
        assertThat(vo.getSqlQuery()).isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("内部元数据：查不到返回 null（供 analysis 区分\"无记录\"与\"调用失败\"），不抛异常")
    void should_returnNull_when_getDatasetMetaMissing() {
        when(datasetMapper.selectById(404L)).thenReturn(null);

        // Then 与 getDatasetById 的 404 语义相反：这里 null 是合法答案
        assertThat(datasetService.getDatasetMeta(404L)).isNull();
    }

    @Test
    @DisplayName("内部元数据：回填 datasetId 并暴露租户/数据源/来源，够 analysis 拼 FROM")
    void should_exposeExecutionMeta_when_getDatasetMetaFound() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "dwd_x", "SELECT 1"));

        DatasetMetaVO meta = datasetService.getDatasetMeta(10L);

        assertThat(meta.getDatasetId()).isEqualTo(10L);
        assertThat(meta.getTenantId()).isEqualTo("t-1");
        assertThat(meta.getDatasourceId()).isEqualTo(7L);
        assertThat(meta.getTableName()).isEqualTo("dwd_x");
        assertThat(meta.getSqlQuery()).isEqualTo("SELECT 1");
    }

    // ==================== previewDataset — 前置校验 ====================

    @Test
    @DisplayName("预览：数据集不存在直接抛 404")
    void should_throwNotFound_when_previewMissingDataset() {
        when(datasetMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> datasetService.previewDataset(404L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("预览：未绑定数据源时拒绝，且不去执行任何 SQL")
    void should_throwBadRequest_when_previewHasNoDatasource() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, null, "dwd_x", null));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集未绑定数据源，无法预览");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    // ==================== previewDataset — 自定义 SQL 单语句只读 ====================

    @Test
    @DisplayName("预览自定义 SQL：只读单语句，LIMIT 直接接在裸 SQL 之后（与 analysis 包成子查询分叉）")
    void should_buildReadonlyShape_when_previewCustomSql() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, null, "SELECT id, name FROM t"));
        when(queryClient.query(any(), any(), any(), any())).thenReturn(result());

        DatasetPreviewVO vo = datasetService.previewDataset(10L, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).query(eq(7L), sqlCaptor.capture(), eq(100), isNull());
        // 与 analysis 的分叉点：预览是 `sql LIMIT n`，analysis 是 `(sql) t_source` 再自带 LIMIT
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT id, name FROM t LIMIT 100");
        assertThat(vo.getSql()).isEqualTo("SELECT id, name FROM t LIMIT 100");
        assertThat(vo.getColumns()).containsExactly("id", "amount");
        assertThat(vo.getRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("预览自定义 SQL：含第二条语句（分号在中间）直接拒绝，不执行")
    void should_rejectMultiStatement_when_previewCustomSql() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, null, "SELECT 1; DROP TABLE x"));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集自定义 SQL 含多条语句，拒绝预览");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("预览自定义 SQL：非 SELECT 开头（写操作）拒绝")
    void should_rejectNonSelect_when_previewCustomSql() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, null, "DELETE FROM t WHERE 1=1"));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集自定义 SQL 不是只读查询，拒绝预览");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("预览自定义 SQL：容忍结尾分号，去掉后仍按单语句放行")
    void should_stripTrailingSemicolon_when_previewCustomSql() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, null, "SELECT 1 ;  "));
        when(queryClient.query(any(), any(), any(), any())).thenReturn(result());

        datasetService.previewDataset(10L, 100);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).query(eq(7L), sqlCaptor.capture(), eq(100), isNull());
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT 1 LIMIT 100");
    }

    // ==================== previewDataset — 物理表标识符白名单 ====================

    @Test
    @DisplayName("预览物理表：合法标识符包成反引号全表 LIMIT")
    void should_buildWhitelistedTable_when_previewPhysicalTable() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "dwd_sales_daily", null));
        when(queryClient.query(any(), any(), any(), any())).thenReturn(result());

        datasetService.previewDataset(10L, 500);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryClient).query(eq(7L), sqlCaptor.capture(), eq(500), isNull());
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM `dwd_sales_daily` LIMIT 500");
    }

    @Test
    @DisplayName("预览物理表：表名不是合法标识符（含分号/空格）直接拒绝，白名单正则与 analysis 同源")
    void should_rejectIllegalIdentifier_when_previewPhysicalTable() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "sales; DROP TABLE x", null));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集表名不是合法标识符: sales; DROP TABLE x");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("预览物理表：以数字开头的表名不匹配 [A-Za-z_] 起始，拒绝")
    void should_rejectDigitLeadingTable_when_previewPhysicalTable() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "1table", null));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集表名不是合法标识符");
        verify(queryClient, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("预览：表名与自定义 SQL 都没配时拒绝（此分支用默认 500 码，与 analysis 的 400 分叉）")
    void should_throw_when_previewHasNeitherTableNorSql() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "  ", "  "));

        assertThatThrownBy(() -> datasetService.previewDataset(10L, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集未配置查询SQL或表名");
    }

    // ==================== previewDataset — 行数夹取与空结果 ====================

    @Test
    @DisplayName("预览行数：null/0/负数都回落到默认 100，上限夹到 1000")
    void should_clampRows_when_previewLimitVarious() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "t", null));
        when(queryClient.query(any(), any(), any(), any())).thenReturn(result());

        datasetService.previewDataset(10L, 5000);
        ArgumentCaptor<Integer> c1 = ArgumentCaptor.forClass(Integer.class);
        verify(queryClient).query(eq(7L), any(), c1.capture(), isNull());
        assertThat(c1.getValue()).isEqualTo(1000);

        // null → 默认 100（getValue() 取本次捕获序列的最后一条）
        datasetService.previewDataset(10L, null);
        ArgumentCaptor<Integer> c2 = ArgumentCaptor.forClass(Integer.class);
        verify(queryClient, org.mockito.Mockito.times(2)).query(eq(7L), any(), c2.capture(), isNull());
        assertThat(c2.getValue()).isEqualTo(100);

        // 0 → 默认 100（!limit>0）
        datasetService.previewDataset(10L, 0);
        ArgumentCaptor<Integer> c3 = ArgumentCaptor.forClass(Integer.class);
        verify(queryClient, org.mockito.Mockito.times(3)).query(eq(7L), any(), c3.capture(), isNull());
        assertThat(c3.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("预览：底层返回空列/空行时收敛成空集合且 rowCount=0，不是 null")
    void should_returnEmptyCollections_when_previewResultHasNulls() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L, 7L, "t", null));
        DatasourceQueryResult empty = new DatasourceQueryResult();
        empty.setColumns(null);
        empty.setRows(null);
        when(queryClient.query(any(), any(), any(), any())).thenReturn(empty);

        DatasetPreviewVO vo = datasetService.previewDataset(10L, 100);

        assertThat(vo.getColumns()).isEqualTo(Collections.emptyList());
        assertThat(vo.getRows()).isEqualTo(Collections.emptyList());
        assertThat(vo.getRowCount()).isEqualTo(0);
    }
}
