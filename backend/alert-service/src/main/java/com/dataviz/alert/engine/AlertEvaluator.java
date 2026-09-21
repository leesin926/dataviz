package com.dataviz.alert.engine;

import com.dataviz.alert.entity.AlertRule;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

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

    /** 目前能真正判定的只有阈值型；DERIVATIVE/COMPOSITE 需要多期数据，未实现时显式跳过而不是蒙一条值进去 */
    public static final String TYPE_THRESHOLD = "THRESHOLD";

    private final DatasourceQueryClient datasourceQueryClient;

    public Result evaluate(AlertRule rule) {
        if (!TYPE_THRESHOLD.equalsIgnoreCase(rule.getType())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "规则类型 " + rule.getType() + " 暂不支持自动判定（当前只实现 THRESHOLD）");
        }
        if (rule.getDatasourceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则未指定数据源");
        }
        if (!StringUtils.hasText(rule.getMetricExpression())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则未填写指标 SQL（metric_expression）");
        }
        if (rule.getThreshold() == null || !StringUtils.hasText(rule.getCondition())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规则缺少比较条件或阈值");
        }

        BigDecimal value = datasourceQueryClient.queryScalar(rule.getDatasourceId(), rule.getMetricExpression());
        if (value == null) {
            // 查询成功但没有结果行：这是"无法判定"，不能算成"未越界"
            log.warn("规则 {} 指标查询无结果，本轮不判定", rule.getId());
            return new Result(null, false, "指标查询无结果");
        }
        boolean hit = compare(value, rule.getCondition(), rule.getThreshold());
        return new Result(value, hit, null);
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
