package com.dataviz.analysis.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.entity.DataPermRule;
import com.dataviz.analysis.mapper.DataPermRuleMapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 行/列数据权限的规则来源与命中过程（API-21）。
 * <p>
 * 取代原先的 {@code PermissionFilter} —— 那一边读 {@code perm:row:} / {@code perm:col:} 两个 Redis 键，
 * 而全后端没有任何一处写它们，所以"数据权限"实际上从来没有规则进来过。
 * <p>
 * 语义三条，都在 DDL 注释里落了字：规则之间 <b>OR</b>（授权叠加取并集）、规则内条件 <b>AND</b>、
 * 数据集存在启用规则而当前用户一条都不命中则 <b>deny-all</b>（配了规则还让人看全表，等于规则白配）。
 * 同一口径也用在另一半：命中的规则里若有条件在当前账号上求不出值，这些条件一律<b>不放开</b> ——
 * 全部求不出时同样 deny-all，而不是退回"没限制"。超管跳过行列限制，与 RBAC 拦截器的短路口径保持一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionResolver {

    static final String PLACEHOLDER_USER_ID = ":userId";
    static final String PLACEHOLDER_TENANT_ID = ":tenantId";
    static final String PLACEHOLDER_USERNAME = ":username";
    static final String PLACEHOLDER_DEPT_ID = ":deptId";

    private static final Set<String> KNOWN_PLACEHOLDERS = new LinkedHashSet<>(java.util.Arrays.asList(
            PLACEHOLDER_USER_ID, PLACEHOLDER_TENANT_ID, PLACEHOLDER_USERNAME, PLACEHOLDER_DEPT_ID));

    private final DataPermRuleMapper ruleMapper;
    private final SqlBuilder sqlBuilder;
    private final ObjectMapper objectMapper;

    /**
     * @param tenantId  请求侧租户（网关头），只用于占位符；表本身的租户隔离由 MP 租户拦截器负责
     * @param userId    请求侧用户
     * @param datasetId 本次查询的数据集
     */
    public DataPermission resolve(Long tenantId, Long userId, Long datasetId) {
        LoginUser current = SecurityContextHolder.getLoginUser();
        if (current != null && current.isSuperAdmin()) {
            return DataPermission.unrestricted();
        }

        List<DataPermRule> candidates = loadRules(datasetId);
        if (candidates.isEmpty()) {
            // 这个数据集没配任何规则 ⇒ 与本补丁之前逐字一致，不拼任何谓词
            return DataPermission.unrestricted();
        }

        Set<String> roles = current == null || current.getRoles() == null
                ? Collections.<String>emptySet() : current.getRoles();

        Set<String> restrictedColumns = new LinkedHashSet<>();
        List<String> ruleNames = new ArrayList<>();
        List<String> rulePredicates = new ArrayList<>();
        int conditionsExpected = 0;

        for (DataPermRule rule : candidates) {
            if (!scopeMatches(rule, userId, roles)) {
                continue;
            }
            ruleNames.add(nameOf(rule));
            for (String column : splitColumns(rule.getRestrictedColumns())) {
                restrictedColumns.add(column);
            }
            List<AnalysisQueryDTO.FilterConfig> conditions = parseConditions(rule);
            if (conditions.isEmpty()) {
                continue;
            }
            conditionsExpected++;
            String rendered = renderRule(rule, conditions, current, tenantId, userId);
            if (rendered != null) {
                rulePredicates.add(rendered);
            }
        }

        if (ruleNames.isEmpty()) {
            // 有规则、但一条都不属于这个人：宁可让他查不到，也不能看成"没限制"
            log.info("数据集 {} 存在 {} 条启用规则，用户 {} 未命中任何一条 ⇒ 拒绝返回数据",
                    datasetId, candidates.size(), userId);
            return DataPermission.deniedAll(restrictedColumns, ruleNames, candidates.size());
        }

        if (conditionsExpected > rulePredicates.size()) {
            log.warn("数据集 {} 的 {} 条规则里有 {} 条条件无法在当前账号上求值，已按更严格处理（这些条件不会放开）",
                    datasetId, conditionsExpected, conditionsExpected - rulePredicates.size());
        }

        if (rulePredicates.isEmpty() && conditionsExpected > 0) {
            // 命中的规则本来要限行，但条件在这个账号上求不出值。此时若走下面的"行上不加限制"，
            // 症状是"配了规则的人反而看全表"，与上面那句更严格相反 —— 与"一条都不命中"同一口径。
            return DataPermission.deniedAll(restrictedColumns, ruleNames, candidates.size());
        }

        String predicate = rulePredicates.isEmpty()
                // 命中规则但规则都只配了列权限 ⇒ 行上不加限制
                ? "" : "(" + String.join(") OR (", rulePredicates) + ")";

        return DataPermission.of(predicate, restrictedColumns, ruleNames, candidates.size());
    }

    /** 管理端预览用：命中哪几条、渲染成什么谓词、遮哪些列。存完规则不至于只能靠猜它生不生效。 */
    public DataPermission explain(Long tenantId, Long userId, Long datasetId) {
        return resolve(tenantId, userId, datasetId);
    }

    private List<DataPermRule> loadRules(Long datasetId) {
        LambdaQueryWrapper<DataPermRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataPermRule::getEnabled, 1);
        if (datasetId != null) {
            wrapper.and(w -> w.isNull(DataPermRule::getDatasetId).or().eq(DataPermRule::getDatasetId, datasetId));
            wrapper.orderByAsc(DataPermRule::getId);
        } else {
            wrapper.isNull(DataPermRule::getDatasetId);
        }
        return ruleMapper.selectList(wrapper);
    }

    private static boolean scopeMatches(DataPermRule rule, Long userId, Set<String> roles) {
        String value = rule.getScopeValue() == null ? "" : rule.getScopeValue().trim();
        if ("USER".equalsIgnoreCase(rule.getScopeType())) {
            return userId != null && String.valueOf(userId).equals(value);
        }
        if ("ROLE".equalsIgnoreCase(rule.getScopeType())) {
            return roles.contains(value);
        }
        // 认不出的命中方式一律不匹配（库里若被塞进脏数据，症状是"这条规则没生效"，不是"所有人都被限制"）
        return false;
    }

    private String renderRule(DataPermRule rule, List<AnalysisQueryDTO.FilterConfig> conditions,
                              LoginUser current, Long tenantId, Long userId) {
        List<AnalysisQueryDTO.FilterConfig> resolved = new ArrayList<>(conditions.size());
        for (AnalysisQueryDTO.FilterConfig condition : conditions) {
            Object value;
            try {
                value = resolvePlaceholders(condition.getValue(), current, tenantId, userId);
            } catch (BizException e) {
                throw new BizException(e.getCode(), "规则「" + nameOf(rule) + "」" + e.getMessage());
            }
            if (value == UNRESOLVED) {
                // 当前账号取不到这个占位符（例如没有部门）⇒ 本条规则的放行范围不生效，只往严格方向偏
                return null;
            }
            AnalysisQueryDTO.FilterConfig copy = new AnalysisQueryDTO.FilterConfig();
            copy.setField(condition.getField());
            copy.setOperator(condition.getOperator());
            copy.setValue(value);
            resolved.add(copy);
        }
        try {
            return sqlBuilder.permissionPredicate(resolved);
        } catch (BizException e) {
            throw new BizException(e.getCode(), "规则「" + nameOf(rule) + "」渲染失败：" + e.getMessage());
        }
    }

    /** 占位符求不出值时的哨兵（null 本身是合法的条件值，例如 isnull）。 */
    private static final Object UNRESOLVED = new Object();

    private Object resolvePlaceholders(Object raw, LoginUser current, Long tenantId, Long userId) {
        if (raw instanceof String) {
            return resolveOne((String) raw, current, tenantId, userId);
        }
        if (raw instanceof List) {
            List<?> source = (List<?>) raw;
            List<Object> out = new ArrayList<>(source.size());
            for (Object item : source) {
                Object value = item instanceof String
                        ? resolveOne((String) item, current, tenantId, userId) : item;
                if (value == UNRESOLVED) {
                    return UNRESOLVED;
                }
                out.add(value);
            }
            return out;
        }
        return raw;
    }

    private Object resolveOne(String text, LoginUser current, Long tenantId, Long userId) {
        String value = text.trim();
        if (!value.startsWith(":")) {
            return text;
        }
        if (!KNOWN_PLACEHOLDERS.contains(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "使用了未知占位符 " + value
                    + "（可用：" + String.join(" / ", KNOWN_PLACEHOLDERS) + "）");
        }
        if (PLACEHOLDER_USER_ID.equals(value)) {
            return userId;
        }
        if (PLACEHOLDER_TENANT_ID.equals(value)) {
            if (tenantId != null) {
                return tenantId;
            }
            return current == null ? UNRESOLVED : current.getTenantId();
        }
        if (PLACEHOLDER_USERNAME.equals(value)) {
            return current == null || current.getUsername() == null ? UNRESOLVED : current.getUsername();
        }
        return current == null || current.getDeptId() == null ? UNRESOLVED : current.getDeptId();
    }

    private List<AnalysisQueryDTO.FilterConfig> parseConditions(DataPermRule rule) {
        String json = rule.getConditionsJson();
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<AnalysisQueryDTO.FilterConfig> parsed = objectMapper.readValue(json,
                    new TypeReference<List<AnalysisQueryDTO.FilterConfig>>() {
                    });
            return parsed == null ? Collections.<AnalysisQueryDTO.FilterConfig>emptyList() : parsed;
        } catch (Exception e) {
            // 规则存进去读不出来，绝不能当成"这条规则没有条件"（那等于放开全表）
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "权限规则「" + nameOf(rule) + "」的行条件无法解析，请重新保存该规则");
        }
    }

    public static List<String> splitColumns(String restrictedColumns) {
        List<String> out = new ArrayList<>();
        if (restrictedColumns == null || restrictedColumns.trim().isEmpty()) {
            return out;
        }
        for (String column : restrictedColumns.split(",")) {
            String trimmed = column.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    static String nameOf(DataPermRule rule) {
        return rule.getName() == null || rule.getName().trim().isEmpty()
                ? "规则 #" + rule.getId() : rule.getName().trim();
    }

    /** 保存规则时用它挡掉拼错的占位符，免得等到用户查询时才炸。 */
    public static boolean isPlaceholder(Object value) {
        return value instanceof String && ((String) value).trim().startsWith(":");
    }

    public static boolean isKnownPlaceholder(Object value) {
        return isPlaceholder(value) && KNOWN_PLACEHOLDERS.contains(((String) value).trim());
    }

    public static Set<String> knownPlaceholders() {
        return Collections.unmodifiableSet(KNOWN_PLACEHOLDERS);
    }
}
