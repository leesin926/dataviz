package com.dataviz.alert.engine;

import com.dataviz.alert.entity.AlertRule;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 规则判定：把 {@code metric_expression} 当作<b>返回单值的只读 SQL</b>，取首行首列与阈值比较。
 * <p>
 * 只读执行、连接池、超时与行数上限都在 datasource-service 侧（{@code /api/datasource/internal/query}），
 * 本服务不再自己拿 JDBC 口令。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 阈值型：指标 SQL 返回一个数，直接与阈值比 */
    public static final String TYPE_THRESHOLD = "THRESHOLD";

    /**
     * 变化率型：{@code metric_expression} 放一段 JSON {@code {"current":"SELECT ...","previous":"SELECT ..."}}，
     * 两条 SQL 各回一个数，判的是 <b>环比变化百分比</b> —— {@code LT -10} 读作"跌幅超过 10%"。
     * 之所以是两条标量查询而不是一条多列 SQL：{@link DatasourceQueryClient#queryScalar} 是本服务唯一的取数口，
     * 多列形状要在 datasource-service 侧另开一条契约，属另一轮。
     */
    public static final String TYPE_DERIVATIVE = "DERIVATIVE";

    /** 组合型仍未实现：它需要子规则引用完整性 + 环检测，还要给事件文案定一个"当前值"的口径 */
    public static final String TYPE_COMPOSITE = "COMPOSITE";

    private final DatasourceQueryClient datasourceQueryClient;
    private final ObjectMapper objectMapper;

    public Result evaluate(AlertRule rule) {
        String type = rule.getType() == null ? TYPE_THRESHOLD : rule.getType().trim().toUpperCase();
        if (TYPE_DERIVATIVE.equals(type)) {
            return evaluateDerivative(rule);
        }
        if (!TYPE_THRESHOLD.equals(type)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "规则类型 " + rule.getType() + " 暂不支持自动判定：当前可实现的是 THRESHOLD（阈值）与 "
                            + TYPE_DERIVATIVE + "（环比变化率）；" + TYPE_COMPOSITE
                            + " 需要子规则引用校验与环检测，未随本轮交付");
        }
        return evaluateThreshold(rule, readScalar(rule));
    }

    /** 阈值型主体；取数已由调用方完成，便于 THRESHOLD 与 DERIVATIVE 共用比较逻辑 */
    private Result evaluateThreshold(AlertRule rule, BigDecimal value) {
        if (rule.getThreshold() == null || !StringUtils.hasText(rule.getCondition())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则缺少比较条件或阈值");
        }
        if (value == null) {
            // 查询成功但没有结果行：这是"无法判定"，不能算成"未越界"
            log.warn("规则 {} 指标查询无结果，本轮不判定", rule.getId());
            return new Result(null, false, "指标查询无结果");
        }
        boolean hit = compare(value, rule.getCondition(), rule.getThreshold());
        return new Result(value, hit, null);
    }

    /**
     * 变化率型：上期为 0 时变化率<b>无定义</b>，这不是"未越界"也不是"越界"，而是"无法判定" ——
     * 与 {@link #readScalar} 空结果同一档处理，调用方（AlertCheckServiceImpl）会保留已累计的越界状态。
     */
    private Result evaluateDerivative(AlertRule rule) {
        if (rule.getDatasourceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则未指定数据源");
        }
        if (!StringUtils.hasText(rule.getMetricExpression())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "DERIVATIVE 规则的 metric_expression 需为 JSON：{\"current\":\"SELECT ...\",\"previous\":\"SELECT ...\"}");
        }
        String currentSql;
        String previousSql;
        try {
            JsonNode config = objectMapper.readTree(rule.getMetricExpression());
            currentSql = textOf(config, "current");
            previousSql = textOf(config, "previous");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "DERIVATIVE 规则的 metric_expression 不是合法 JSON：" + e.getMessage());
        }

        BigDecimal current = datasourceQueryClient.queryScalar(rule.getDatasourceId(), currentSql);
        BigDecimal previous = datasourceQueryClient.queryScalar(rule.getDatasourceId(), previousSql);
        if (current == null || previous == null) {
            log.warn("规则 {} 的{}查询无结果，本轮不判定", rule.getId(),
                    current == null ? "本期" : "上期");
            return new Result(null, false, current == null ? "本期指标查询无结果" : "上期指标查询无结果");
        }
        if (previous.signum() == 0) {
            return new Result(null, false, "上期为 0，变化率无定义（除零）");
        }
        BigDecimal rate = current.subtract(previous)
                .divide(previous.abs(), 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
        return evaluateThreshold(rule, rate);
    }

    private String textOf(JsonNode config, String key) {
        JsonNode node = config.get(key);
        if (node == null || !StringUtils.hasText(node.asText())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "DERIVATIVE 规则的 metric_expression 缺少 " + key + " 字段");
        }
        return node.asText();
    }

    private BigDecimal readScalar(AlertRule rule) {
        if (rule.getDatasourceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则未指定数据源");
        }
        if (!StringUtils.hasText(rule.getMetricExpression())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则未填写指标 SQL（metric_expression）");
        }
        return datasourceQueryClient.queryScalar(rule.getDatasourceId(), rule.getMetricExpression());
    }

    /**
     * 未知条件必须抛错：落到 default 返回 false 就等于静默漏报。
     */
    private boolean compare(BigDecimal value, String condition, BigDecimal threshold) {
        int cmp = value.compareTo(threshold);
        switch (condition.toUpperCase()) {
            case "GT":
                return cmp > 0;
            case "LT":
                return cmp < 0;
            case "GTE":
                return cmp >= 0;
            case "LTE":
                return cmp <= 0;
            case "EQ":
                return cmp == 0;
            default:
                throw new BizException(ErrorCode.BAD_REQUEST, "不支持的告警条件: " + condition);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class Result {
        /** null 表示取不到可比较的值 */
        private final BigDecimal value;
        private final boolean triggered;
        private final String note;
    }
}
