package com.dataviz.analysis.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.common.core.exception.BizException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ORDER BY 段的解析（API-109）。
 * <p>
 * 大屏上"按销售额倒序"的条形图/饼图是配置里最常见的一页，但它原来拼成 {@code ORDER BY `amount` DESC}：
 * GROUP BY 之后 amount 既不是分组列也不是聚合值，only_full_group_by 直接 1140 报错。
 * 维度恰好是唯一键时 MySQL 认定函数依赖、侥幸通过，所以同一个写法在会员等级图上活着、在门店区域图上死了 ——
 * 接口仍是 200，页面只剩一个空图，看的人以为数据集没数据。
 * 这里把"排序命中指标必须走投影别名"钉住，顺带钉住别名与 SELECT 侧同源（改名要两头一起变）。
 */
class SqlBuilderOrderByTest {

    private final SqlBuilder sqlBuilder = new SqlBuilder();

    private static DatasetMeta tableMeta(String table) {
        DatasetMeta meta = new DatasetMeta();
        meta.setTableName(table);
        return meta;
    }

    private static AnalysisQueryDTO query(String dim, String metricField, String metricAlias, String orderField) {
        AnalysisQueryDTO q = new AnalysisQueryDTO();
        AnalysisQueryDTO.DimensionConfig d = new AnalysisQueryDTO.DimensionConfig();
        d.setField(dim);
        q.setDimensions(Collections.singletonList(d));

        AnalysisQueryDTO.MetricConfig m = new AnalysisQueryDTO.MetricConfig();
        m.setField(metricField);
        m.setAggFunction("sum");
        m.setAlias(metricAlias);
        q.setMetrics(Collections.singletonList(m));

        AnalysisQueryDTO.OrderConfig o = new AnalysisQueryDTO.OrderConfig();
        o.setField(orderField);
        o.setDirection("desc");
        q.setOrders(Collections.singletonList(o));
        return q;
    }

    @Test
    @DisplayName("按指标排序时排投影别名，不再排裸列")
    void shouldOrderByMetricAlias_whenOrderFieldIsAMetric() {
        String sql = sqlBuilder.buildSql(query("region", "amount", null, "amount"), tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("SUM(`amount`) AS `sum_amount`");
        assertThat(sql).contains("ORDER BY `sum_amount` DESC");
        assertThat(sql).doesNotContain("ORDER BY `amount`");
    }

    @Test
    @DisplayName("指标带自定义别名时，排序沿用同一个别名")
    void shouldOrderByCustomAlias_whenMetricHasAlias() {
        String sql = sqlBuilder.buildSql(query("region", "amount", "total_amount", "amount"),
                tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("AS `total_amount`");
        assertThat(sql).contains("ORDER BY `total_amount` DESC");
    }

    @Test
    @DisplayName("排序字段是维度时仍按列排，不受指标别名影响")
    void shouldOrderByColumn_whenOrderFieldIsADimension() {
        String sql = sqlBuilder.buildSql(query("region", "amount", null, "region"),
                tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("ORDER BY `region` DESC");
    }

    @Test
    @DisplayName("同字段多聚合时取第一个聚合的别名，与 SELECT 里的先后一致")
    void shouldPreferFirstMetricAlias_whenFieldAggregatedTwice() {
        AnalysisQueryDTO q = query("region", "amount", null, "amount");
        AnalysisQueryDTO.MetricConfig second = new AnalysisQueryDTO.MetricConfig();
        second.setField("amount");
        second.setAggFunction("avg");
        q.setMetrics(Arrays.asList(q.getMetrics().get(0), second));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("SUM(`amount`) AS `sum_amount`");
        assertThat(sql).contains("AVG(`amount`) AS `avg_amount`");
        assertThat(sql).contains("ORDER BY `sum_amount` DESC");
    }

    @Test
    @DisplayName("排序字段带杂字符时清洗成单个标识符，不把注入片段拼进 SQL")
    void shouldSanitize_whenOrderFieldCarriesJunk() {
        AnalysisQueryDTO q = query("region", "amount", null, "amount; DROP TABLE x");

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).endsWith("ORDER BY `amountDROPTABLEx` DESC LIMIT 1000");
        assertThat(sql).doesNotContain(";");
    }

    @Test
    @DisplayName("排序方向不在 ASC/DESC 白名单里时当场拒绝")
    void shouldReject_whenDirectionIsNotAscOrDesc() {
        AnalysisQueryDTO q = query("region", "amount", null, "region");
        q.getOrders().get(0).setDirection("DESC, (SELECT 1)");

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的排序方向");
    }

    @Test
    @DisplayName("自定义 SQL 数据集：外层排序同样走别名，不落到子查询的裸列")
    void shouldOrderByAlias_whenSourceIsSubquery() {
        DatasetMeta meta = new DatasetMeta();
        meta.setSqlQuery("SELECT store_name, amount FROM dws_store_sales");

        String sql = sqlBuilder.buildSql(query("store_name", "amount", null, "amount"), meta);

        assertThat(sql).contains("FROM (SELECT store_name, amount FROM dws_store_sales) t_source");
        assertThat(sql).contains("ORDER BY `sum_amount` DESC");
    }
}
