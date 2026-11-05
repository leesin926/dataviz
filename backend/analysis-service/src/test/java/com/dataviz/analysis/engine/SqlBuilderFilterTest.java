package com.dataviz.analysis.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.common.core.exception.BizException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 过滤段（WHERE / HAVING）、行级权限谓词与标识符判据的契约。
 * <p>
 * 这条 SQL 要经 HTTP 交给 datasource-service 执行，没有参数绑定可退，所以"值怎么加引号"和
 * "操作符走不走白名单"是同一件事的两面：任何一处原样透传，前端的配置就变成了 SQL 的一部分。
 * 本类把每个操作符的渲染形状、每种取值的引号规则、以及越界时的拒绝消息逐字钉住；
 * 少数"实际行为不等于理想行为"的分支按现状记录（见【记录实际行为】的用例），不改生产代码。
 * </p>
 */
class SqlBuilderFilterTest {

    private static final String TABLE = "dwd_orders";

    private final SqlBuilder sqlBuilder = new SqlBuilder();

    private static DatasetMeta meta() {
        DatasetMeta meta = new DatasetMeta();
        meta.setTableName(TABLE);
        return meta;
    }

    private static AnalysisQueryDTO.FilterConfig filter(String field, String operator, Object value) {
        AnalysisQueryDTO.FilterConfig f = new AnalysisQueryDTO.FilterConfig();
        f.setField(field);
        f.setOperator(operator);
        f.setValue(value);
        return f;
    }

    private static AnalysisQueryDTO.MetricFilterConfig metricFilter(String field, String agg,
                                                                    String operator, Object value) {
        AnalysisQueryDTO.MetricFilterConfig f = new AnalysisQueryDTO.MetricFilterConfig();
        f.setField(field);
        f.setAggFunction(agg);
        f.setOperator(operator);
        f.setValue(value);
        return f;
    }

    /** 只带 filters 的查询：投影退化成 *，断言可以只看 WHERE 段本身 */
    private static AnalysisQueryDTO filtered(AnalysisQueryDTO.FilterConfig... filters) {
        AnalysisQueryDTO q = new AnalysisQueryDTO();
        q.setDatasetId(1L);
        q.setDimensions(new ArrayList<AnalysisQueryDTO.DimensionConfig>());
        q.setMetrics(new ArrayList<AnalysisQueryDTO.MetricConfig>());
        q.setFilters(new ArrayList<>(Arrays.asList(filters)));
        return q;
    }

    /** 维度 region + 指标 sum(amount) as total_sales，只换 metricFilters */
    private static AnalysisQueryDTO grouped(AnalysisQueryDTO.MetricFilterConfig... metricFilters) {
        AnalysisQueryDTO q = new AnalysisQueryDTO();
        q.setDatasetId(1L);
        AnalysisQueryDTO.DimensionConfig d = new AnalysisQueryDTO.DimensionConfig();
        d.setField("region");
        q.setDimensions(new ArrayList<>(Collections.singletonList(d)));
        AnalysisQueryDTO.MetricConfig m = new AnalysisQueryDTO.MetricConfig();
        m.setField("amount");
        m.setAggFunction("sum");
        m.setAlias("total_sales");
        q.setMetrics(new ArrayList<>(Collections.singletonList(m)));
        q.setMetricFilters(new ArrayList<>(Arrays.asList(metricFilters)));
        return q;
    }

    /**
     * 剥掉所有单引号字面量（串内 '' 视为转义）之后剩下的 SQL 骨架。
     * 注入判据要用骨架来断言：字面量里出现分号是安全的，骨架里出现分号才是真的漏了。
     */
    private static String withoutLiterals(String sql) {
        StringBuilder skeleton = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c != '\'') {
                skeleton.append(c);
                i++;
                continue;
            }
            i++;
            while (i < sql.length()) {
                if (sql.charAt(i) == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    i++;
                    break;
                }
                i++;
            }
            skeleton.append('@');
        }
        return skeleton.toString();
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("词形与符号形两套操作符都映射到规范符号，条件之间用 AND 串起来")
    void should_renderCanonicalOperators_when_filtersUseWordOrSymbolForms() {
        AnalysisQueryDTO q = filtered(
                filter("status", "eq", "paid"),
                filter("status", "ne", "cancelled"),
                filter("amount", "gt", 100),
                filter("amount", "gte", 100),
                filter("amount", "lt", 100),
                filter("amount", "lte", 100),
                filter("cnt", "=", 100),
                filter("cnt", "!=", 100),
                filter("cnt", "<>", 100),
                filter("cnt", "<=", 100));

        String sql = sqlBuilder.buildSql(q, meta());

        assertThat(sql).isEqualTo("SELECT * FROM `dwd_orders` WHERE `status` = 'paid'"
                + " AND `status` <> 'cancelled'"
                + " AND `amount` > 100 AND `amount` >= 100 AND `amount` < 100 AND `amount` <= 100"
                + " AND `cnt` = 100 AND `cnt` <> 100 AND `cnt` <> 100 AND `cnt` <= 100"
                + " LIMIT 1000");
        // 操作符名不能原样进 SQL：出现 " eq " 就说明白名单退化成了透传
        assertThat(sql).doesNotContain(" eq ").doesNotContain(" gte ").doesNotContain("!=");
    }

    @Test
    @DisplayName("操作符先按大小写/下划线/空格清洗再查白名单，多词操作符也能命中")
    void should_normaliseOperatorName_when_operatorHasCaseUnderscoreOrSpace() {
        AnalysisQueryDTO q = filtered(
                filter("status", "NOT_IN", Arrays.asList("a", "b")),
                filter("status", "not like", "x"),
                filter("amount", " Gte ", 100),
                filter("cnt", "EQ", 1),
                filter("amount", "NOT_BETWEEN", Arrays.asList(1, 10)));

        String sql = sqlBuilder.buildSql(q, meta());

        assertThat(sql).contains("`status` NOT IN ('a', 'b')")
                .contains("`status` NOT LIKE '%x%'")
                .contains("`amount` >= 100")
                .contains("`cnt` = 1")
                .contains("`amount` NOT BETWEEN 1 AND 10");
        assertThat(sql).doesNotContain("NOT_IN").doesNotContain("NOT_BETWEEN").doesNotContain("EQ");
    }

    @Test
    @DisplayName("操作符不认识或缺失 - 抛错，绝不走 else 分支原样拼接")
    void should_reject_when_operatorUnknownOrMissing() {
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("status", "regex", "a")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的过滤操作符: regex");

        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("status", null, "a")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤条件缺少操作符");

        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("status", "", "a")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的过滤操作符");

        // 关键：连"长得像 SQL 片段"的操作符也进不了白名单
        assertThatThrownBy(() -> sqlBuilder
                .buildSql(filtered(filter("status", "= 'a' OR 1=1 --", "a")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的过滤操作符");
    }

    @Test
    @DisplayName("数值与布尔不加引号，数字形状的字符串照旧加引号")
    void should_quoteOnlyNonNumericValues_when_valueTypeDiffers() {
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "eq", 100)), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `amount` = 100 LIMIT 1000");
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "eq", 5.5d)), meta()))
                .contains("`amount` = 5.5");
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "eq", 99999999999L)), meta()))
                .contains("`amount` = 99999999999");

        // 引号规则：字符串 "12" 走字面量分支，必须带引号，否则比较语义就变了
        assertThat(sqlBuilder.buildSql(filtered(filter("age", "eq", "12")), meta()))
                .contains("`age` = '12'").doesNotContain("`age` = 12");

        // 布尔按 1/0 落地；日期对象走 toString 之后加引号
        assertThat(sqlBuilder.buildSql(filtered(filter("valid", "eq", Boolean.TRUE)), meta()))
                .contains("`valid` = 1");
        assertThat(sqlBuilder.buildSql(filtered(filter("valid", "eq", Boolean.FALSE)), meta()))
                .contains("`valid` = 0");
        assertThat(sqlBuilder.buildSql(filtered(filter("order_date", "eq", LocalDate.of(2024, 1, 1))), meta()))
                .contains("`order_date` = '2024-01-01'");

        // NaN 也是 Number，但不是有限数值，拒
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("amount", "eq", Double.NaN)), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤值必须是有限数值");
    }

    @Test
    @DisplayName("字面量转义：单引号成对、反斜杠倍化、制表符放行、其他控制字符与超长取值拒绝")
    void should_escapeQuotesAndBackslashes_when_valueIsNotPlainText() {
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "eq", "O'Brien")), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `name` = 'O''Brien' LIMIT 1000");

        String breakout = sqlBuilder.buildSql(
                filtered(filter("name", "eq", "'; DROP TABLE users; --")), meta());

        assertThat(breakout).isEqualTo("SELECT * FROM `dwd_orders`"
                + " WHERE `name` = '''; DROP TABLE users; --' LIMIT 1000");
        // 骨架（剥掉字面量之后）里既没有分号也没有注释符，说明用户串没能逃出引号
        String skeleton = withoutLiterals(breakout);
        assertThat(skeleton).isEqualTo("SELECT * FROM `dwd_orders` WHERE `name` = @ LIMIT 1000");
        assertThat(skeleton).doesNotContain(";").doesNotContain("--").doesNotContain("DROP");

        assertThat(sqlBuilder.buildSql(filtered(filter("path", "eq", "C:\\temp\\a")), meta()))
                .contains("`path` = 'C:\\\\temp\\\\a'");
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "eq", "a\tb")), meta()))
                .contains("`name` = 'a\tb'");
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "eq", repeat('a', 1024))), meta()))
                .contains("`name` = '" + repeat('a', 1024) + "'");

        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("name", "eq", "a\nb")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤值含非法控制字符");
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("name", "eq", "a\u0000b")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤值含非法控制字符");
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("name", "eq", repeat('a', 1025))), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤值过长（上限 1024 字符）");
    }

    @Test
    @DisplayName("IN / NOT IN：数组逐项加引号、逗号串按逗号拆；取值不是数组或为空则拒绝")
    void should_renderOrRejectInList_when_valueShapeDoesNotFit() {
        assertThat(sqlBuilder.buildSql(filtered(filter("region", "in", Arrays.asList("east", "west"))), meta()))
                .contains("WHERE `region` IN ('east', 'west')");
        assertThat(sqlBuilder.buildSql(filtered(filter("dept_id", "notin", Arrays.asList(1, 2))), meta()))
                .contains("WHERE `dept_id` NOT IN (1, 2)");
        assertThat(sqlBuilder.buildSql(filtered(filter("region", "in", Collections.singletonList("east"))), meta()))
                .contains("WHERE `region` IN ('east')");
        // 逗号串按 split(",") 处理，第二个元素前的空格是用户的一部分，不裁
        assertThat(sqlBuilder.buildSql(filtered(filter("region", "in", "east, west")), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `region` IN ('east', ' west') LIMIT 1000");

        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("region", "in", "east")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("IN 需要一个数组作为取值");

        assertThatThrownBy(() -> sqlBuilder
                .buildSql(filtered(filter("region", "in", new ArrayList<String>())), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("IN 的取值列表不能为空");

        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("region", "notin", "")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("NOT IN 需要一个数组作为取值");

        // 比较类操作符拿到数组同样拒，不能静默渲染成 '[a, b]'
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("region", "eq", Arrays.asList("a", "b"))), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该操作符的取值不接受数组");
    }

    @Test
    @DisplayName("BETWEEN 渲染成 BETWEEN x AND y；边界数量不是两个则拒绝")
    void should_renderOrRejectBetween_when_boundCountDiffers() {
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "between", Arrays.asList(1, 10))), meta()))
                .contains("WHERE `amount` BETWEEN 1 AND 10");
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "notbetween", Arrays.asList(1, 10))), meta()))
                .contains("WHERE `amount` NOT BETWEEN 1 AND 10");
        assertThat(sqlBuilder.buildSql(filtered(filter("order_date", "between",
                Arrays.asList("2024-01-01", "2024-01-31"))), meta()))
                .contains("WHERE `order_date` BETWEEN '2024-01-01' AND '2024-01-31'");
        assertThat(sqlBuilder.buildSql(filtered(filter("amount", "between", "1,10")), meta()))
                .contains("WHERE `amount` BETWEEN '1' AND '10'");

        assertThatThrownBy(() -> sqlBuilder
                .buildSql(filtered(filter("amount", "between", Arrays.asList(1, 2, 3))), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BETWEEN 需要两个边界值");
        assertThatThrownBy(() -> sqlBuilder
                .buildSql(filtered(filter("amount", "between", Collections.singletonList(1))), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BETWEEN 需要两个边界值");
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("amount", "between", 5)), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("BETWEEN 需要一个数组作为取值");
    }

    @Test
    @DisplayName("IS NULL / IS NOT NULL 只出谓词，条件里的 value 被丢掉；比较类取值缺失则报错")
    void should_renderNullPredicateWithoutValue_when_operatorIsNullCheck() {
        assertThat(sqlBuilder.buildSql(filtered(filter("deleted_at", "isnull", "SENTINEL")), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `deleted_at` IS NULL LIMIT 1000")
                .doesNotContain("SENTINEL");
        assertThat(sqlBuilder.buildSql(filtered(filter("deleted_at", "isnotnull", null)), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `deleted_at` IS NOT NULL LIMIT 1000");
        assertThat(sqlBuilder.buildSql(filtered(filter("deleted_at", "is_null", null)), meta()))
                .contains("WHERE `deleted_at` IS NULL");

        // 同一个 null 取值在比较类操作符下是报错，不是静默变成 IS NULL 或 ''
        assertThatThrownBy(() -> sqlBuilder.buildSql(filtered(filter("status", "eq", null)), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过滤值不能为空");
    }

    @Test
    @DisplayName("【记录实际行为】LIKE 不校验空值：null 取值被渲染成字符串 'null' 的匹配模式")
    void should_matchLiteralNullText_when_likeValueIsMissing() {
        String sql = sqlBuilder.buildSql(filtered(filter("name", "like", null)), meta());

        // Then 字面量是 '%null%'，会匹配到名字里真含 "null" 的行，而不是"没配值"的语义
        assertThat(sql).isEqualTo("SELECT * FROM `dwd_orders` WHERE `name` LIKE '%null%' LIMIT 1000");
        assertThat(sql).doesNotContain("IS NULL");
    }

    @Test
    @DisplayName("LIKE 两头加 %，用户串里的 % 与 _ 转义成字面量")
    void should_escapeWildcardsAndWrapPercent_when_operatorIsLike() {
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "like", "abc")), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `name` LIKE '%abc%' LIMIT 1000");
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "like", "100%")), meta()))
                .contains("`name` LIKE '%100\\%%'");
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "like", "a_b")), meta()))
                .contains("`name` LIKE '%a\\_b%'");
        // 引号转义与通配转义同时生效
        assertThat(sqlBuilder.buildSql(filtered(filter("name", "like", "O'Brien%")), meta()))
                .contains("`name` LIKE '%O''Brien\\%%'");
    }

    @Test
    @DisplayName("HAVING 重新展开聚合（不引用投影别名），只收比较符与数值，其余拒绝")
    void should_reaggregateAndValidate_when_metricFiltersPresent() {
        String sql = sqlBuilder.buildSql(grouped(metricFilter("amount", "sum", "gt", 100)), meta());
        assertThat(sql).isEqualTo("SELECT `region`, SUM(`amount`) AS `total_sales` FROM `dwd_orders`"
                + " GROUP BY `region` HAVING SUM(`amount`) > 100 LIMIT 1000");
        assertThat(sql).doesNotContain("HAVING `total_sales`");

        // aggFunction 缺省按 SUM；字符串取值走数值判据后不加引号
        assertThat(sqlBuilder.buildSql(grouped(
                metricFilter("id", "count", "gt", 5),
                metricFilter("amount", null, "lte", "1000")), meta()))
                .contains("HAVING COUNT(`id`) > 5 AND SUM(`amount`) <= 1000");
        assertThat(sqlBuilder.buildSql(grouped(metricFilter("amount", "sum", "gt", "1e3")), meta()))
                .contains("HAVING SUM(`amount`) > 1e3");
        assertThat(sqlBuilder.buildSql(grouped(), meta())).doesNotContain("HAVING");

        assertThatThrownBy(() -> sqlBuilder.buildSql(grouped(
                metricFilter("amount", "sum", "in", Arrays.asList(1, 2))), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("指标过滤只支持数值比较，收到: IN");

        assertThatThrownBy(() -> sqlBuilder.buildSql(grouped(
                metricFilter("amount", "sum", "like", "1")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("指标过滤只支持数值比较，收到: LIKE");

        assertThatThrownBy(() -> sqlBuilder.buildSql(grouped(
                metricFilter("amount", "sum", "gt", "abc")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该过滤条件要求数值，收到: abc");

        // 数值判据挡住注入：带符号的 "+5" 这种写法同样进不了 SQL
        assertThatThrownBy(() -> sqlBuilder.buildSql(grouped(
                metricFilter("amount", "sum", "gt", "1; DROP TABLE x")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该过滤条件要求数值，收到: 1; DROP TABLE x");
        assertThatThrownBy(() -> sqlBuilder.buildSql(grouped(
                metricFilter("amount", "sum", "gt", "+5")), meta()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该过滤条件要求数值");
    }

    @Test
    @DisplayName("权限谓词与业务 filters 走同一渲染：字段加反引号、值加引号、条件之间 AND")
    void should_joinConditionsWithAnd_when_permissionPredicateBuilt() {
        List<AnalysisQueryDTO.FilterConfig> conditions = Arrays.asList(
                filter("dept_id", "eq", 7),
                filter("region", "eq", "east"));

        assertThat(sqlBuilder.permissionPredicate(conditions))
                .isEqualTo("`dept_id` = 7 AND `region` = 'east'");
        assertThat(sqlBuilder.permissionPredicate(Collections.<AnalysisQueryDTO.FilterConfig>emptyList()))
                .isEmpty();
        assertThat(sqlBuilder.permissionPredicate(null)).isEmpty();
        assertThat(sqlBuilder.permissionPredicate(Collections.singletonList(
                filter("deleted_at", "isnull", null)))).isEqualTo("`deleted_at` IS NULL");
    }

    @Test
    @DisplayName("权限条件同样过标识符与操作符白名单；未替换的占位符落成一个普通字符串字面量")
    void should_runPermissionConditionsThroughTheSameWhitelist_when_ruleCarriesJunk() {
        assertThat(sqlBuilder.permissionPredicate(Collections.singletonList(
                filter("dept_id; DROP TABLE x", "eq", 1))))
                .isEqualTo("`dept_idDROPTABLEx` = 1");

        assertThatThrownBy(() -> sqlBuilder.permissionPredicate(Collections.singletonList(
                filter("dept_id", "1=1 OR", 1))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的过滤操作符");

        assertThatThrownBy(() -> sqlBuilder.permissionPredicate(Collections.singletonList(
                filter("部门", "eq", 1))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("权限字段名无效: 部门");

        // 占位符替换是上游的事；漏替换时这里不会报错，而是变成一个匹配不到任何行的常量
        assertThat(sqlBuilder.permissionPredicate(Collections.singletonList(
                filter("dept_id", "eq", ":deptId"))))
                .isEqualTo("`dept_id` = ':deptId'");
    }

    @Test
    @DisplayName("权限谓词并进 WHERE 并保持括号，永远排在 GROUP BY / HAVING / ORDER BY / LIMIT 之前")
    void should_placePermissionBeforeGroupByAndLimit_when_predicatePassed() {
        AnalysisQueryDTO q = grouped(metricFilter("amount", "sum", "gt", 100));
        AnalysisQueryDTO.OrderConfig order = new AnalysisQueryDTO.OrderConfig();
        order.setField("amount");
        order.setDirection("desc");
        q.setOrders(Collections.singletonList(order));
        q.setFilters(new ArrayList<>(Collections.singletonList(filter("status", "eq", "paid"))));

        String sql = sqlBuilder.buildSql(q, meta(), null, null, "`dept_id` = 7");

        assertThat(sql).contains("WHERE `status` = 'paid' AND (`dept_id` = 7) GROUP BY `region`");
        int where = sql.indexOf("WHERE");
        int groupBy = sql.indexOf("GROUP BY");
        int having = sql.indexOf("HAVING");
        int orderBy = sql.indexOf("ORDER BY");
        int limit = sql.indexOf("LIMIT");
        assertThat(where).isGreaterThanOrEqualTo(0).isLessThan(groupBy);
        assertThat(groupBy).isLessThan(having);
        assertThat(having).isLessThan(orderBy);
        assertThat(orderBy).isLessThan(limit);
        assertThat(sql).endsWith(" LIMIT 1000");
    }

    @Test
    @DisplayName("WHERE 只在有 filters 或有权限谓词时出现；独占的谓词带括号，空白或未传谓词不拼")
    void should_wrapPermissionAsSoleWhereClause_when_noBusinessFilters() {
        AnalysisQueryDTO plain = new AnalysisQueryDTO();
        plain.setDatasetId(1L);
        AnalysisQueryDTO emptyFilterList = filtered();

        // 没配 filters（null 与空列表同价）时整段 WHERE 不出现，而不是 WHERE ()
        assertThat(sqlBuilder.buildSql(plain, meta())).isEqualTo("SELECT * FROM `dwd_orders` LIMIT 1000");
        assertThat(sqlBuilder.buildSql(emptyFilterList, meta())).isEqualTo("SELECT * FROM `dwd_orders` LIMIT 1000");
        assertThat(sqlBuilder.buildSql(plain, meta(), null, null, null)).doesNotContain("WHERE");

        assertThat(sqlBuilder.buildSql(plain, meta(), null, null, "`dept_id` = 7"))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE (`dept_id` = 7) LIMIT 1000");

        AnalysisQueryDTO withFilters = filtered(filter("region", "eq", "east"));
        assertThat(sqlBuilder.buildSql(withFilters, meta(), null, null, "`dept_id` = 7"))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `region` = 'east' AND (`dept_id` = 7) LIMIT 1000");

        assertThat(sqlBuilder.buildSql(plain, meta(), null, null, "   "))
                .isEqualTo("SELECT * FROM `dwd_orders` LIMIT 1000");
    }

    @Test
    @DisplayName("【记录实际行为】第五个入参不做二次校验：未过白名单的文本原样进 WHERE，注释符吞掉后续子句")
    void should_concatenateVerbatim_when_permissionPredicateIsPassedUnsanitized() {
        AnalysisQueryDTO q = new AnalysisQueryDTO();
        q.setDatasetId(1L);
        q.setDimensions(new ArrayList<>(Collections.singletonList(new AnalysisQueryDTO.DimensionConfig())));
        q.getDimensions().get(0).setField("region");

        String sql = sqlBuilder.buildSql(q, meta(), null, null, "1 = 1) OR (`a` = 1 --");

        // Then 调用方给什么就拼什么，行级权限的信任边界完全在 SqlBuilder 之外
        assertThat(sql).isEqualTo("SELECT `region` FROM `dwd_orders` WHERE (1 = 1) OR (`a` = 1 --)"
                + " GROUP BY `region` LIMIT 1000");
        assertThat(withoutLiterals(sql)).contains("--").contains(") OR (");
    }

    @Test
    @DisplayName("requireIdentifier 要求字段「本来就是」合法标识符：只裁两侧空白，其余拒绝并带上标签")
    void should_rejectUntrimmedOrPaddedNames_when_requireIdentifierRuns() {
        assertThat(SqlBuilder.requireIdentifier(" dept_id ", "条件字段")).isEqualTo("dept_id");
        assertThat(SqlBuilder.requireIdentifier("_dept", "条件字段")).isEqualTo("_dept");
        assertThat(SqlBuilder.requireIdentifier(repeat('a', 64), "条件字段")).hasSize(64);

        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("", "条件字段"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件字段不能为空");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier(null, "受限列"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限列不能为空");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("   ", "受限列"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限列不能为空");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("salary; drop", "条件字段"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件字段不是合法列名: salary; drop");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("`dept`", "条件字段"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件字段不是合法列名");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("1dept", "条件字段"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件字段不是合法列名: 1dept");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier(repeat('a', 65), "条件字段"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("条件字段不是合法列名");
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("部门", "受限列"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限列不是合法列名: 部门");
    }

    @Test
    @DisplayName("同一串脏字段名：规则保存侧拒绝，查询侧静默清洗成另一个列名")
    void should_divergeFromQuerySide_when_cleaningAndStrictCheckMeet() {
        // Given 同一个写法分别走两条判据
        assertThatThrownBy(() -> SqlBuilder.requireIdentifier("dept-id", "受限列"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限列不是合法列名: dept-id");

        // Then 查询侧不报错，清洗成 deptid —— 真实列若是 dept_id，症状是"查出来是空"
        assertThat(sqlBuilder.buildSql(filtered(filter("dept-id", "eq", "x")), meta()))
                .isEqualTo("SELECT * FROM `dwd_orders` WHERE `deptid` = 'x' LIMIT 1000");
        assertThat(sqlBuilder.buildSql(filtered(filter("dept-id", "eq", "x")), meta()))
                .doesNotContain("`dept-id`").doesNotContain("`dept_id`");
    }
}
