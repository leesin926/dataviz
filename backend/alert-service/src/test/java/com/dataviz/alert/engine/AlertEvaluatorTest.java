package com.dataviz.alert.engine;

import com.dataviz.alert.entity.AlertRule;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阶段 BE5：告警判定的三档口径。
 * <p>
 * THRESHOLD 判的是"这个数越没越界"；DERIVATIVE 判的是<b>环比变化率</b>（跌幅/涨幅），
 * 所以它的阈值是百分数，{@code LT -10} 读作"比上期跌超 10%"。
 * 两者都把"取不到数"和"上期为 0"归入<b>无法判定</b>（{@code value == null}），
 * 这条口径不是装饰：调用方 {@code AlertCheckServiceImpl} 只有在"确实回到阈值内"时才清掉
 * 已累计的越界时长，把无法判定混成未越界会让长 duration 的规则永远凑不满（API-112）。
 */
class AlertEvaluatorTest {

    private DatasourceQueryClient client;
    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        client = mock(DatasourceQueryClient.class);
        evaluator = new AlertEvaluator(client, new ObjectMapper());
    }

    private AlertRule thresholdRule(String condition, String threshold) {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setName("销售额跌破 1000");
        rule.setType("THRESHOLD");
        rule.setDatasourceId(901L);
        rule.setMetricExpression("SELECT SUM(amount) FROM db_analysis.dwd_sales_daily");
        rule.setCondition(condition);
        rule.setThreshold(new BigDecimal(threshold));
        return rule;
    }

    private AlertRule derivativeRule(String condition, String threshold, String currentSql, String previousSql) {
        AlertRule rule = thresholdRule(condition, threshold);
        rule.setType("DERIVATIVE");
        rule.setMetricExpression("{\"current\":\"" + currentSql + "\",\"previous\":\"" + previousSql + "\"}");
        return rule;
    }

    @Test
    @DisplayName("阈值型 - 越界即触发，返回值就是查到的那个数")
    void should_trigger_when_thresholdBreached() {
        // Given
        AlertRule rule = thresholdRule("GT", "80");
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(new BigDecimal("95"));

        // When
        AlertEvaluator.Result result = evaluator.evaluate(rule);

        // Then
        assertTrue(result.isTriggered());
        assertEquals(new BigDecimal("95"), result.getValue());
        assertNull(result.getNote());
    }

    @Test
    @DisplayName("阈值型 - 未越界不触发，也不带说明")
    void should_notTrigger_when_withinThreshold() {
        AlertRule rule = thresholdRule("GT", "80");
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(new BigDecimal("12"));

        assertFalse(evaluator.evaluate(rule).isTriggered());
    }

    @ParameterizedTest
    @CsvSource({"GT,80,80.01,true", "GTE,80,80,true", "LT,80,79,true", "LTE,80,80,true", "EQ,80,80,true",
            "GT,80,80,false", "LT,80,80,false", "EQ,80,81,false"})
    @DisplayName("阈值型 - 六个比较符的边界取值按各自的开闭语义判定")
    void should_compareByOperator_when_boundaryValues(String condition, String threshold, String value,
                                                      boolean expected) {
        AlertRule rule = thresholdRule(condition, threshold);
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(new BigDecimal(value));

        assertEquals(expected, evaluator.evaluate(rule).isTriggered(),
                condition + " " + threshold + " vs " + value);
    }

    @Test
    @DisplayName("阈值型 - 未知比较符必须抛错，落到默认分支等于静默漏报")
    void should_throw_when_conditionUnknown() {
        AlertRule rule = thresholdRule("BETWEEN", "80");
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(new BigDecimal("90"));

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("不支持的告警条件"), e.getMessage());
    }

    @Test
    @DisplayName("阈值型 - 大小写与空格的类型写法仍按 THRESHOLD 处理")
    void should_trimAndUpperCase_when_typeWrittenLoosely() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setType(" threshold ");
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(new BigDecimal("99"));

        assertTrue(evaluator.evaluate(rule).isTriggered());
    }

    @Test
    @DisplayName("取数成功但零行 - 判为无法判定而不是未越界")
    void should_reportUndecidable_when_queryHasNoRow() {
        AlertRule rule = thresholdRule("GT", "80");
        when(client.queryScalar(901L, rule.getMetricExpression())).thenReturn(null);

        AlertEvaluator.Result result = evaluator.evaluate(rule);
        assertNull(result.getValue());
        assertFalse(result.isTriggered());
        assertEquals("指标查询无结果", result.getNote());
    }

    @Test
    void should_throw_when_thresholdMissing() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setThreshold(null);

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("缺少比较条件或阈值"), e.getMessage());
    }

    @Test
    void should_throw_when_datasourceMissing() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setDatasourceId(null);

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertEquals("规则未指定数据源", e.getMessage());
    }

    @Test
    void should_throw_when_metricExpressionBlank() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setMetricExpression("  ");

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("metric_expression"), e.getMessage());
    }

    @Test
    @DisplayName("变化率型 - 跌 10% 命中 LT -5")
    void should_computeRate_when_derivativeFalls() {
        // Given
        AlertRule rule = derivativeRule("LT", "-5",
                "SELECT SUM(amount) FROM t WHERE d='2026-11-05'",
                "SELECT SUM(amount) FROM t WHERE d='2026-11-04'");
        when(client.queryScalar(901L, "SELECT SUM(amount) FROM t WHERE d='2026-11-05'"))
                .thenReturn(new BigDecimal("90"));
        when(client.queryScalar(901L, "SELECT SUM(amount) FROM t WHERE d='2026-11-04'"))
                .thenReturn(new BigDecimal("100"));

        // When
        AlertEvaluator.Result result = evaluator.evaluate(rule);

        // Then：变化率 = (90-100)/|100|*100 = -10.00
        assertTrue(result.isTriggered());
        assertEquals(0, result.getValue().compareTo(new BigDecimal("-10.00")), result.getValue().toString());
        assertEquals("-10.00", result.getValue().toString());
    }

    @Test
    @DisplayName("变化率型 - 涨 5% 不命中 LT -5，但命中 GT 4")
    void should_signCorrectly_when_derivativeRises() {
        AlertRule up = derivativeRule("LT", "-5", "SELECT 105", "SELECT 100");
        when(client.queryScalar(901L, "SELECT 105")).thenReturn(new BigDecimal("105"));
        when(client.queryScalar(901L, "SELECT 100")).thenReturn(new BigDecimal("100"));

        assertFalse(evaluator.evaluate(up).isTriggered());
        assertEquals(0, evaluator.evaluate(up).getValue().compareTo(new BigDecimal("5.00")));

        AlertRule gt = derivativeRule("GT", "4", "SELECT 105", "SELECT 100");
        assertTrue(evaluator.evaluate(gt).isTriggered());
    }

    @Test
    @DisplayName("变化率型 - 上期为 0 时变化率无定义，归无法判定（不抛、也不算越界）")
    void should_reportUndecidable_when_previousIsZero() {
        AlertRule rule = derivativeRule("GT", "10", "SELECT 7", "SELECT 0");
        when(client.queryScalar(901L, "SELECT 7")).thenReturn(new BigDecimal("7"));
        when(client.queryScalar(901L, "SELECT 0")).thenReturn(BigDecimal.ZERO);

        AlertEvaluator.Result result = evaluator.evaluate(rule);
        assertNull(result.getValue());
        assertFalse(result.isTriggered());
        assertTrue(result.getNote().contains("上期为 0"), result.getNote());
    }

    @Test
    @DisplayName("变化率型 - 上期为负时用绝对值取分母，跌幅符号不反")
    void should_useAbsoluteDenominator_when_previousNegative() {
        AlertRule rule = derivativeRule("LT", "-50", "SELECT -60", "SELECT -100");
        when(client.queryScalar(901L, "SELECT -60")).thenReturn(new BigDecimal("-60"));
        when(client.queryScalar(901L, "SELECT -100")).thenReturn(new BigDecimal("-100"));

        // (-60 - -100) / |-100| * 100 = 40.00（亏损收窄 40%）
        assertEquals("40.00", evaluator.evaluate(rule).getValue().toString());
        assertFalse(evaluator.evaluate(rule).isTriggered());
    }

    @Test
    void should_reportUndecidable_when_currentQueryHasNoRow() {
        AlertRule rule = derivativeRule("LT", "-5", "SELECT c", "SELECT p");
        when(client.queryScalar(901L, "SELECT c")).thenReturn(null);
        when(client.queryScalar(901L, "SELECT p")).thenReturn(new BigDecimal("100"));

        AlertEvaluator.Result result = evaluator.evaluate(rule);
        assertNull(result.getValue());
        assertEquals("本期指标查询无结果", result.getNote());
    }

    @Test
    void should_reportUndecidable_when_previousQueryHasNoRow() {
        AlertRule rule = derivativeRule("LT", "-5", "SELECT c2", "SELECT p2");
        when(client.queryScalar(901L, "SELECT c2")).thenReturn(new BigDecimal("90"));
        when(client.queryScalar(901L, "SELECT p2")).thenReturn(null);

        assertEquals("上期指标查询无结果", evaluator.evaluate(rule).getNote());
    }

    @Test
    @DisplayName("变化率型 - 缺 previous 字段就点名缺哪个，不猜一条 SQL 去跑")
    void should_throw_when_derivativeConfigMissingKey() {
        AlertRule rule = thresholdRule("LT", "-5");
        rule.setType("DERIVATIVE");
        rule.setMetricExpression("{\"current\":\"SELECT 1\"}");

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("previous"), e.getMessage());
    }

    @Test
    void should_throw_when_derivativeConfigNotJson() {
        AlertRule rule = thresholdRule("LT", "-5");
        rule.setType("DERIVATIVE");
        rule.setMetricExpression("SELECT 1");

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("不是合法 JSON"), e.getMessage());
    }

    @Test
    void should_throw_when_derivativeExpressionBlank() {
        AlertRule rule = thresholdRule("LT", "-5");
        rule.setType("DERIVATIVE");
        rule.setMetricExpression(null);

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("current"), e.getMessage());
    }

    @Test
    @DisplayName("组合型仍未实现 - 但要说清缺什么，而不是只说「当前只实现 THRESHOLD」")
    void should_explainWhatIsMissing_when_compositeRule() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setType("COMPOSITE");

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("子规则"), e.getMessage());
        assertTrue(e.getMessage().contains("环检测"), e.getMessage());
    }

    @Test
    void should_listSupportedTypes_when_typeUnknown() {
        AlertRule rule = thresholdRule("GT", "80");
        rule.setType("SPIKE");

        BizException e = assertThrows(BizException.class, () -> evaluator.evaluate(rule));
        assertTrue(e.getMessage().contains("THRESHOLD"), e.getMessage());
        assertTrue(e.getMessage().contains("DERIVATIVE"), e.getMessage());
        assertTrue(e.getMessage().contains("SPIKE"), e.getMessage());
    }
}
