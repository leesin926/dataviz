package com.dataviz.analysis.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 投影段与来源段的契约（API-109 的另一半）。
 * <p>
 * {@link SqlBuilder} 产出的是整条拼好的 SQL，前端按别名取数、DB 按标识符执行，两头都只认文本，
 * 所以"别名怎么推导""来源怎么包""GROUP BY 落了哪几列"这三件事必须逐字钉住：
 * 别名规则一改，前端拿到的 JSON key 就变了；来源规则一改，图表就从有数变空数。
 * ORDER BY 段的别名替换另有 {@link SqlBuilderOrderByTest} 守，本类只补它没覆盖的分支。
 * </p>
 */
class SqlBuilderProjectionTest {

    private final SqlBuilder sqlBuilder = new SqlBuilder();

    private static DatasetMeta tableMeta(String tableName) {
        DatasetMeta meta = new DatasetMeta();
        meta.setTableName(tableName);
        return meta;
    }

    private static DatasetMeta sqlMeta(String sqlQuery) {
        DatasetMeta meta = new DatasetMeta();
        meta.setSqlQuery(sqlQuery);
        return meta;
    }

    private static AnalysisQueryDTO.DimensionConfig dim(String field) {
        AnalysisQueryDTO.DimensionConfig d = new AnalysisQueryDTO.DimensionConfig();
        d.setField(field);
        return d;
    }

    private static AnalysisQueryDTO.DimensionConfig dim(String field, String dateFormat) {
        AnalysisQueryDTO.DimensionConfig d = dim(field);
        d.setDateFormat(dateFormat);
        return d;
    }

    private static AnalysisQueryDTO.MetricConfig metric(String field, String agg, String alias) {
        AnalysisQueryDTO.MetricConfig m = new AnalysisQueryDTO.MetricConfig();
        m.setField(field);
        m.setAggFunction(agg);
        m.setAlias(alias);
        return m;
    }

    private static AnalysisQueryDTO query(List<AnalysisQueryDTO.DimensionConfig> dimensions,
                                          AnalysisQueryDTO.MetricConfig... metrics) {
        AnalysisQueryDTO q = new AnalysisQueryDTO();
        q.setDatasetId(1L);
        q.setDimensions(dimensions == null ? new ArrayList<>() : new ArrayList<>(dimensions));
        q.setMetrics(new ArrayList<>(Arrays.asList(metrics)));
        return q;
    }

    /** SQL 文本里同一标识符可能同时出现在 SELECT 与 GROUP BY，"恰好一次"这种断言不能靠 contains 表达 */
    private static int countOf(String sql, String fragment) {
        int count = 0;
        int from = 0;
        for (;;) {
            int at = sql.indexOf(fragment, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + fragment.length();
        }
    }

    @Test
    @DisplayName("指标不给别名 - 别名由聚合函数小写前缀加字段名推导")
    void should_deriveAliasFromFuncAndField_when_metricHasNoAlias() {
        // Given 只带一个维度与一个 sum 指标
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")), metric("amount", "sum", null));

        // When
        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        // Then 整条 SQL 逐字固定：投影顺序、别名写法、GROUP BY、默认 LIMIT 一起变就会漏测
        assertThat(sql).isEqualTo(
                "SELECT `region`, SUM(`amount`) AS `sum_amount` FROM `dwd_sales_daily` GROUP BY `region` LIMIT 1000");
        assertThat(sql).doesNotContain("AS `SUM_amount`").doesNotContain("AS sum_amount");
    }

    @Test
    @DisplayName("每种聚合函数都用各自的小写前缀起别名")
    void should_prefixAliasWithFuncName_when_aggIsAvgCountMaxMin() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")),
                metric("score", "avg", null),
                metric("id", "count", null),
                metric("amount", "max", null),
                metric("amount", "min", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("AVG(`score`) AS `avg_score`")
                .contains("COUNT(`id`) AS `count_id`")
                .contains("MAX(`amount`) AS `max_amount`")
                .contains("MIN(`amount`) AS `min_amount`");
        // 别名规则换成大写前缀或不带前缀，本断言就红
        assertThat(sql).doesNotContain("COUNT(DISTINCT").doesNotContain("AS `sum_");
    }

    @Test
    @DisplayName("count distinct 的两种写法都展开成 COUNT(DISTINCT x)，别名前缀分别是 countdistinct 与 distinct")
    void should_expandCountDistinct_when_aggIsDistinctSpelling() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")),
                metric("user_id", "count_distinct", null),
                metric("order_id", "distinct", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("COUNT(DISTINCT `user_id`) AS `countdistinct_user_id`")
                .contains("COUNT(DISTINCT `order_id`) AS `distinct_order_id`");
        assertThat(sql).doesNotContain("COUNT_DISTINCT(`user_id`)");
    }

    @Test
    @DisplayName("聚合函数不在白名单里 - 拒绝，不原样拼进 SQL")
    void should_reject_when_aggFunctionNotWhitelisted() {
        assertThatThrownBy(() -> sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("amount", "stddev", null)),
                tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的聚合函数: STDDEV");

        // "count distinct" 中间是空格而不是下划线，清洗规则救不回来，同样拒绝
        assertThatThrownBy(() -> sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("amount", "count distinct", null)),
                tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的聚合函数: COUNT DISTINCT");
    }

    @Test
    @DisplayName("指标带自定义别名 - 用自定义别名，不再走推导规则")
    void should_useSuppliedAlias_when_metricAliasIsPresent() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")),
                metric("amount", "sum", "total_amount"));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).contains("SUM(`amount`) AS `total_amount`").doesNotContain("`sum_amount`");
    }

    @Test
    @DisplayName("别名先按 [^a-zA-Z0-9_] 清洗：中英混排只剩 ASCII；全非 ASCII 时回退成推导别名")
    void should_stripIllegalCharsFromAlias_when_aliasCarriesOtherScripts() {
        // Given "毛 额/GMV" —— 中文、空格、斜杠都不在标识符白名单里
        String withMixedAlias = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("amount", "sum", "毛 额/GMV")),
                tableMeta("dwd_sales_daily"));

        // Then 只剩 GMV，且清洗后的别名不带任何引号或空格
        assertThat(withMixedAlias).contains("SELECT `region`, SUM(`amount`) AS `GMV` FROM");
        assertThat(withMixedAlias).doesNotContain("毛").doesNotContain("/");

        // Given 纯中文别名：清洗后为空串，退回 func_field
        String pureCjkAlias = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("amount", "sum", "毛额")),
                tableMeta("dwd_sales_daily"));

        assertThat(pureCjkAlias).contains("SUM(`amount`) AS `sum_amount`").doesNotContain("毛额");
    }

    @Test
    @DisplayName("别名清洗后只剩一个下划线 - 无法作列名，当场拒绝")
    void should_reject_when_aliasCleansToSingleUnderscore() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")), metric("amount", "sum", "_"));

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("指标别名与字段名均无法作为列名");
    }

    @Test
    @DisplayName("字段名按白名单清洗：杂字符剔除、数字开头保留；清洗后为空则带标签抛错")
    void should_cleanFieldToIdentifier_when_fieldCarriesJunkOrIsNonAscii() {
        String cleaned = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("na`me ;drop")), metric("amount", "sum", null)),
                tableMeta("dwd_sales_daily"));

        // Then 反引号被剔除，拼不出第二个标识符；分号彻底消失
        assertThat(cleaned).contains("SELECT `namedrop`, SUM(`amount`) AS `sum_amount`")
                .doesNotContain(";");
        assertThat(cleaned).contains("GROUP BY `namedrop`");

        // Given 数字打头的合法 MySQL 标识符，不能因为清洗把首字符丢掉
        String numericLeading = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("2024_sales")), metric("amount", "sum", null)),
                tableMeta("dwd_sales_daily"));

        assertThat(numericLeading).contains("SELECT `2024_sales`", "SUM(`amount`) AS `sum_amount`");

        // Given 清洗后什么都不剩（纯中文列名 / 空串）→ 只能报错，这里不做"加引号原样用"的兜底
        assertThatThrownBy(() -> sqlBuilder.buildSql(
                query(Collections.singletonList(dim("订单")), metric("amount", "sum", null)),
                tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("维度字段名无效: 订单");

        assertThatThrownBy(() -> sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("", "sum", null)),
                tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("指标字段名无效");
    }

    @Test
    @DisplayName("没有维度就没有 GROUP BY 子句；维度指标都缺时投影退化成 *")
    void should_omitGroupByAndDegradeToStar_when_queryHasNoDimensions() {
        // Given 只有指标：整条 GROUP BY 不出现
        String metricsOnly = sqlBuilder.buildSql(
                query(Collections.<AnalysisQueryDTO.DimensionConfig>emptyList(), metric("amount", "sum", null)),
                tableMeta("dwd_sales_daily"));

        assertThat(metricsOnly).isEqualTo("SELECT SUM(`amount`) AS `sum_amount` FROM `dwd_sales_daily` LIMIT 1000");
        assertThat(metricsOnly).doesNotContain("GROUP BY");

        // Given 维度与指标都没配（null 与空列表同价）：投影退化成 *
        AnalysisQueryDTO nullLists = new AnalysisQueryDTO();
        nullLists.setDatasetId(1L);
        AnalysisQueryDTO emptyLists = query(Collections.<AnalysisQueryDTO.DimensionConfig>emptyList());

        assertThat(sqlBuilder.buildSql(nullLists, tableMeta("dwd_sales_daily")))
                .isEqualTo("SELECT * FROM `dwd_sales_daily` LIMIT 1000");
        assertThat(sqlBuilder.buildSql(emptyLists, tableMeta("dwd_sales_daily")))
                .isEqualTo("SELECT * FROM `dwd_sales_daily` LIMIT 1000");
    }

    @Test
    @DisplayName("GROUP BY 落在且只落在配了的那几个维度上")
    void should_groupOnExactlyTheConfiguredDimensions_when_dimensionsPresent() {
        AnalysisQueryDTO q = query(Arrays.asList(dim("region"), dim("channel")), metric("amount", "sum", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).isEqualTo("SELECT `region`, `channel`, SUM(`amount`) AS `sum_amount`"
                + " FROM `dwd_sales_daily` GROUP BY `region`, `channel` LIMIT 1000");
        assertThat(countOf(sql, "GROUP BY")).isEqualTo(1);
        // 指标字段绝不能混进 GROUP BY：那样聚合就退化成明细行
        assertThat(sql).doesNotContain("GROUP BY `region`, `channel`, `amount`");
    }

    @Test
    @DisplayName("同一维度配两次 - 合并成一条，投影与 GROUP BY 都只出现一次")
    void should_deduplicate_when_sameDimensionConfiguredTwice() {
        AnalysisQueryDTO q = query(Arrays.asList(dim("region"), dim("region")), metric("amount", "sum", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        // Then 同字段同粒度合并：重复列头会让前端按名取值时拿到两列同名的结果（API-108 那一族的形状）
        assertThat(sql).isEqualTo("SELECT `region`, SUM(`amount`) AS `sum_amount`"
                + " FROM `dwd_sales_daily` GROUP BY `region` LIMIT 1000");
    }

    @Test
    @DisplayName("同一字段配两种日期粒度 - 拒绝，因为两条投影会撞成同一个列名")
    void should_reject_when_sameFieldHasTwoGranularities() {
        AnalysisQueryDTO q = query(Arrays.asList(dim("created_at", "day"), dim("created_at", "month")),
                metric("amount", "sum", null));

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("两种日期粒度");
    }

    @Test
    @DisplayName("维度同时又是指标字段 - GROUP BY 用裸列，投影里照样聚合一次")
    void should_groupOnBareColumn_when_dimensionFieldIsAlsoAMetric() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("amount")), metric("amount", "sum", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        assertThat(sql).isEqualTo("SELECT `amount`, SUM(`amount`) AS `sum_amount`"
                + " FROM `dwd_sales_daily` GROUP BY `amount` LIMIT 1000");
        // 聚合里不能引用投影别名，否则变成 SUM 套 SUM
        assertThat(sql).doesNotContain("SUM(`sum_amount`)");
    }

    @Test
    @DisplayName("自定义 SQL 数据集 - 去掉末尾分号后包成 ( ... ) t_source，关键字大小写原样保留")
    void should_wrapCustomSqlAsSourceSubquery_when_datasetHasSqlQuery() {
        // Given 前后有空白、末尾带分号的小写 SQL
        String sql = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("a")), metric("b", "sum", null)),
                sqlMeta("  select a, b from t;  "));

        // Then 尾分号被吃掉，只剩一条语句，整条 SQL 里没有分号
        assertThat(sql).isEqualTo("SELECT `a`, SUM(`b`) AS `sum_b` FROM (select a, b from t) t_source"
                + " GROUP BY `a` LIMIT 1000");
        assertThat(sql).doesNotContain(";");
    }

    @Test
    @DisplayName("自定义 SQL 不是只读查询开头 - 拒绝作为来源")
    void should_reject_when_customSqlIsNotAReadOnlySelect() {
        assertThatThrownBy(() -> sqlBuilder.buildSql(query(null, metric("a", "sum", null)),
                sqlMeta("DELETE FROM t")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是只读查询");

        // CTE 不在 SELECT 前缀白名单里（本轮不改判据口径：要放就得单独定 WITH 的规则）
        assertThatThrownBy(() -> sqlBuilder.buildSql(query(null, metric("a", "sum", null)),
                sqlMeta("WITH s AS (SELECT 1 AS a) SELECT * FROM s")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是只读查询");

        // Then 行注释开头不再算"不是只读"：判据看的是掩掉注释之后的第一个词（API-115 同一处修的过度拒绝）
        String commented = sqlBuilder.buildSql(query(Collections.singletonList(dim("a")), metric("a", "count", null)),
                sqlMeta("-- 说明\nSELECT 1 AS a"));
        assertThat(commented).contains("FROM (-- 说明\nSELECT 1 AS a) t_source");
    }

    @Test
    @DisplayName("自定义 SQL 里出现第二个分号 - 按多条语句拒绝")
    void should_reject_when_customSqlChainsTwoStatements() {
        assertThatThrownBy(() -> sqlBuilder.buildSql(query(null, metric("a", "sum", null)),
                sqlMeta("SELECT a FROM t; DELETE FROM t")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("含多条语句");
    }

    @Test
    @DisplayName("字面量里带分号的只读 SQL - 不再被当成多条语句（API-115 已修）")
    void should_accept_when_customSqlHoldsSemicolonInsideAStringLiteral() {
        // Given 完全只读，只是常量里含分号
        AnalysisQueryDTO q = query(Collections.singletonList(dim("k")), metric("v", "count", null));
        String customSql = "SELECT 'a;b' AS k, 1 AS v FROM dual";

        // Then 分号判据扫的是"掩掉字面量与注释之后"的文本，原样语句仍然进来源子查询
        String sql = sqlBuilder.buildSql(q, sqlMeta(customSql));
        assertThat(sql).contains("FROM (SELECT 'a;b' AS k, 1 AS v FROM dual) t_source");

        // And 真多条语句仍然拒绝 —— 掩码只吃掉字面量里的分号，不吃语法分号
        assertThatThrownBy(() -> sqlBuilder.buildSql(q, sqlMeta("SELECT 'a;b' AS k; DELETE FROM t")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("含多条语句");
    }

    @Test
    @DisplayName("SELECT 之后的写/锁子句被拒绝（API-114 已修）：前缀判据不再单独把关")
    void should_reject_when_customSqlStartsWithSelectButWritesToFile() {
        // Given 以 SELECT 开头、尾部带 INTO OUTFILE（落盘）与 FOR UPDATE（加锁）的语句
        AnalysisQueryDTO q = query(Collections.singletonList(dim("a")), metric("a", "count", null));

        assertThatThrownBy(() -> sqlBuilder.buildSql(q,
                sqlMeta("SELECT a FROM t INTO OUTFILE 'D:/dump/a.csv'")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("INTO OUTFILE");

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, sqlMeta("SELECT a FROM t FOR UPDATE")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("FOR UPDATE");

        // 关键词写在字符串字面量里时不算写操作，否则连"备注列"都建不了数据集
        String benign = sqlBuilder.buildSql(q, sqlMeta("SELECT 'FOR UPDATE' AS a FROM t"));
        assertThat(benign).contains("FROM (SELECT 'FOR UPDATE' AS a FROM t) t_source");
    }

    @Test
    @DisplayName("表名与自定义 SQL 同时配置 - 自定义 SQL 优先")
    void should_preferCustomSql_overTableName() {
        DatasetMeta meta = new DatasetMeta();
        meta.setTableName("dwd_sales_daily");
        meta.setSqlQuery("SELECT region FROM dwd_sales_daily");

        String sql = sqlBuilder.buildSql(
                query(Collections.singletonList(dim("region")), metric("amount", "sum", null)), meta);

        assertThat(sql).contains("FROM (SELECT region FROM dwd_sales_daily) t_source")
                .doesNotContain("FROM `dwd_sales_daily`");
    }

    @Test
    @DisplayName("来源缺失、表名不合法、表名越界 - meta 为空按 404，其余按 400 拒绝")
    void should_reject_when_datasetSourceIsMissingOrTableNameIllegal() {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")), metric("amount", "sum", null));

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集不存在，无法确定查询来源")
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());

        assertThatThrownBy(() -> sqlBuilder.buildSql(q, new DatasetMeta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集未配置表名或自定义 SQL");

        DatasetMeta blankSource = new DatasetMeta();
        blankSource.setTableName("   ");
        assertThatThrownBy(() -> sqlBuilder.buildSql(q, blankSource))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据集未配置表名或自定义 SQL");

        // 库名限定、反引号、注释、非 ASCII 都不是合法的单一标识符
        for (String bad : Arrays.asList("dwd.sales", "`orders`", "orders; --", "orders表")) {
            assertThatThrownBy(() -> sqlBuilder.buildSql(q, tableMeta(bad)))
                    .as("表名 %s 应被拒", bad)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("不是合法标识符");
        }

        // 上限判据：两侧空白可救，64 字符内可用，65 字符拒
        StringBuilder builder = new StringBuilder("a");
        while (builder.length() < 64) {
            builder.append("b");
        }
        String name64 = builder.toString();
        assertThat(sqlBuilder.buildSql(q, tableMeta("  " + name64 + "  ")))
                .contains("FROM `" + name64 + "`");
        assertThatThrownBy(() -> sqlBuilder.buildSql(q, tableMeta(name64 + "b")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是合法标识符");
    }

    @Test
    @DisplayName("日期粒度 - 生成 DATE_FORMAT 分桶并投影回原列名（API-113 已修：此前 dateFormat 零引用）")
    void should_bucketByDateFormat_when_dimensionRequestsGranularity() {
        // Given 一个用粒度词、一个用模式串，两种写法都该认
        AnalysisQueryDTO q = query(Arrays.asList(dim("created_at", "month"), dim("order_date", "yyyy-MM")),
                metric("amount", "sum", null));

        String sql = sqlBuilder.buildSql(q, tableMeta("dwd_sales_daily"));

        // Then 别名仍是列名本身 —— 前端按 dim.field 取值，换名就等于把 API-108 再犯一次
        assertThat(sql).isEqualTo("SELECT DATE_FORMAT(`created_at`, '%Y-%m') AS `created_at`,"
                + " DATE_FORMAT(`order_date`, '%Y-%m') AS `order_date`, SUM(`amount`) AS `sum_amount`"
                + " FROM `dwd_sales_daily`"
                + " GROUP BY DATE_FORMAT(`created_at`, '%Y-%m'), DATE_FORMAT(`order_date`, '%Y-%m') LIMIT 1000");
    }

    @Test
    @DisplayName("日期粒度 - 六种粒度和大小写/下划线写法都落到各自的模式串")
    void should_mapEachGranularity_when_spelledAnyWay() {
        assertThat(bucketOf("day")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-%m-%d')");
        assertThat(bucketOf("HOUR")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-%m-%d %H')");
        assertThat(bucketOf("minute")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-%m-%d %H:%i')");
        assertThat(bucketOf("year")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y')");
        assertThat(bucketOf("quarter")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-Q%q')");
        assertThat(bucketOf("week")).isEqualTo("DATE_FORMAT(`stat_date`, '%x-W%v')");
        assertThat(bucketOf("YYYY_MM_DD")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-%m-%d')");
        assertThat(bucketOf("yyyy-MM-dd HH:mm:ss")).isEqualTo("DATE_FORMAT(`stat_date`, '%Y-%m-%d %H:%i:%s')");
    }

    @Test
    @DisplayName("日期粒度 - 空白即不分桶；认不出来的值拒绝并列出可用粒度，而不是原样塞进 DATE_FORMAT")
    void should_rejectUnknownGranularity_when_notWhitelisted() {
        AnalysisQueryDTO plain = query(Collections.singletonList(dim("created_at", "  ")),
                metric("amount", "sum", null));
        assertThat(sqlBuilder.buildSql(plain, tableMeta("t")))
                .isEqualTo("SELECT `created_at`, SUM(`amount`) AS `sum_amount`"
                        + " FROM `t` GROUP BY `created_at` LIMIT 1000");

        AnalysisQueryDTO bogus = query(Collections.singletonList(dim("created_at", "%Y; DROP")),
                metric("amount", "sum", null));
        assertThatThrownBy(() -> sqlBuilder.buildSql(bogus, tableMeta("t")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的日期粒度")
                .hasMessageContaining("month");
    }

    private String bucketOf(String granularity) {
        AnalysisQueryDTO q = query(Collections.singletonList(dim("stat_date", granularity)),
                metric("amount", "sum", null));
        String sql = sqlBuilder.buildSql(q, tableMeta("dws_store_sales"));
        int start = sql.indexOf("DATE_FORMAT");
        return sql.substring(start, sql.indexOf(" AS `stat_date`"));
    }

    @Test
    @DisplayName("行数天花板与分页：缺省/非正数走默认 1000，超天花板截到 5000，OFFSET 只在正数时出现")
    void should_clampLimitAndOffset_when_queryLimitOrPaginationIsOutOfRange() {
        AnalysisQueryDTO withinCeiling = query(Collections.singletonList(dim("region")),
                metric("amount", "sum", null));
        withinCeiling.setLimit(2000);
        assertThat(sqlBuilder.buildSql(withinCeiling, tableMeta("t"))).endsWith(" LIMIT 2000");

        AnalysisQueryDTO oversized = query(Collections.singletonList(dim("region")),
                metric("amount", "sum", null));
        oversized.setLimit(99999);
        assertThat(sqlBuilder.buildSql(oversized, tableMeta("t")))
                .endsWith(" LIMIT 5000").doesNotContain("99999");

        // limit 缺省 / 0 / 负数都退到默认行数，而不是 LIMIT 0
        AnalysisQueryDTO noLimit = query(Collections.singletonList(dim("region")), metric("amount", "sum", null));
        assertThat(sqlBuilder.buildSql(noLimit, tableMeta("t"))).endsWith(" LIMIT 1000");
        for (Integer missing : Arrays.<Integer>asList(0, -1, null)) {
            AnalysisQueryDTO q = query(Collections.singletonList(dim("region")), metric("amount", "sum", null));
            q.setLimit(missing);
            assertThat(sqlBuilder.buildSql(q, tableMeta("t"))).as("limit=%s", missing).endsWith(" LIMIT 1000");
        }

        // 分页形态：每页各自夹天花板，OFFSET 原样透传
        AnalysisQueryDTO paged = query(Collections.singletonList(dim("region")), metric("amount", "sum", null));
        paged.setLimit(50);
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), 500, 1500))
                .endsWith(" GROUP BY `region` LIMIT 500 OFFSET 1500");
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), 99999, 20000))
                .endsWith(" LIMIT 5000 OFFSET 20000");

        // page 0：offset 为 0 或负数都不写 OFFSET，等价回到第一页而不是报错
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), 500, 0)).doesNotContain("OFFSET");
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), 500, -100)).doesNotContain("OFFSET");

        // rowLimit 非正数被忽略，沿用查询自带 limit（"导出 0 行"落不到 0 行）
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), 0, null)).endsWith(" LIMIT 50");
        assertThat(sqlBuilder.buildSql(paged, tableMeta("t"), null, null)).endsWith(" LIMIT 50");
    }

    @Test
    @DisplayName("自定义 SQL 来源里没这个列时，排序仍走投影别名，不落到子查询的裸列")
    void should_orderByProjectedAlias_when_aliasedMeasureIsNotInCustomSql() {
        // Given 子查询只吐 region，amount 根本不存在；指标别名是自定义的
        DatasetMeta meta = sqlMeta("SELECT region FROM dwd_sales_daily");
        AnalysisQueryDTO q = query(Collections.singletonList(dim("region")),
                metric("amount", "sum", "total_sales"));
        AnalysisQueryDTO.OrderConfig order = new AnalysisQueryDTO.OrderConfig();
        order.setField("amount");
        order.setDirection("desc");
        q.setOrders(Collections.singletonList(order));

        String sql = sqlBuilder.buildSql(q, meta);

        assertThat(sql).isEqualTo("SELECT `region`, SUM(`amount`) AS `total_sales`"
                + " FROM (SELECT region FROM dwd_sales_daily) t_source GROUP BY `region`"
                + " ORDER BY `total_sales` DESC LIMIT 1000");
        assertThat(sql).doesNotContain("ORDER BY `amount`");
    }
}
