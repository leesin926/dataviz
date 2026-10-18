package com.dataviz.analysis.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.analysis.client.ModelServiceClient;
import com.dataviz.analysis.client.dto.DatasetMeta;
import com.dataviz.analysis.dto.AnalysisQueryDTO;
import com.dataviz.analysis.dto.DataPermRuleDTO;
import com.dataviz.analysis.entity.DataPermRule;
import com.dataviz.analysis.engine.DataPermission;
import com.dataviz.analysis.engine.DataPermissionResolver;
import com.dataviz.analysis.engine.SqlBuilder;
import com.dataviz.analysis.mapper.DataPermRuleMapper;
import com.dataviz.analysis.service.DataPermRuleService;
import com.dataviz.analysis.vo.DataPermExplainVO;
import com.dataviz.analysis.vo.DataPermRuleVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 行列权限规则服务。
 * <p>
 * 一条不变的原则：<b>规则在保存时就必须证明它能被渲染</b>。行条件是长期存在的配置，
 * 等到用户查询时才炸，症状是"某个账号突然打不开分析页"，而当事人不知道是谁改了哪条规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPermRuleServiceImpl implements DataPermRuleService {

    /** 占位符在保存时无法求值，用一个形状正确的样例值证明这条条件渲染得出来 */
    private static final String PLACEHOLDER_SAMPLE = "1";

    private final DataPermRuleMapper ruleMapper;
    private final ModelServiceClient modelServiceClient;
    private final DataPermissionResolver resolver;
    private final SqlBuilder sqlBuilder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DataPermRuleDTO dto) {
        DataPermRule entity = toEntity(dto);
        ruleMapper.insert(entity);
        log.info("权限规则已创建: id={}, name={}, scope={}:{}, dataset={}",
                entity.getId(), entity.getName(), entity.getScopeType(), entity.getScopeValue(), entity.getDatasetId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DataPermRuleDTO dto) {
        requireExisting(id);
        DataPermRule entity = toEntity(dto);
        // 可空列（datasetId / restrictedColumns / remark / conditionsJson）只走 .set，不同时挂在实体上：
        // 挂了实体则 MP 的 NOT_NULL 策略会跳过 null ⇒ "清空字段"存不下去；两者都挂则 MySQL 报 Column specified twice
        LambdaUpdateWrapper<DataPermRule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DataPermRule::getId, id)
                .set(DataPermRule::getName, entity.getName())
                .set(DataPermRule::getDatasetId, entity.getDatasetId())
                .set(DataPermRule::getScopeType, entity.getScopeType())
                .set(DataPermRule::getScopeValue, entity.getScopeValue())
                .set(DataPermRule::getConditionsJson, entity.getConditionsJson())
                .set(DataPermRule::getRestrictedColumns, entity.getRestrictedColumns())
                .set(DataPermRule::getEnabled, entity.getEnabled())
                .set(DataPermRule::getRemark, entity.getRemark());
        ruleMapper.update(null, wrapper);
        log.info("权限规则已更新: id={}, name={}", id, entity.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExisting(id);
        ruleMapper.deleteById(id);
        log.info("权限规则已删除: id={}", id);
    }

    @Override
    public DataPermRuleVO detail(Long id) {
        return toVO(requireExisting(id));
    }

    @Override
    public PageResult<DataPermRuleVO> list(String keyword, PageQuery pageQuery) {
        Page<DataPermRule> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<DataPermRule> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(DataPermRule::getName, keyword.trim());
        }
        wrapper.orderByDesc(DataPermRule::getId);
        Page<DataPermRule> result = ruleMapper.selectPage(page, wrapper);
        List<DataPermRuleVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(voList, result.getTotal(), pageQuery.getPageNum(), pageQuery.getPageSize());
    }

    @Override
    public DataPermExplainVO explain(Long datasetId) {
        Long tenantId = parseLong(SecurityContextHolder.getTenantId());
        Long userId = SecurityContextHolder.getUserId();
        DataPermission permission = resolver.explain(tenantId, userId, datasetId);
        DataPermExplainVO vo = new DataPermExplainVO();
        vo.setDenyAll(permission.denyAll());
        vo.setRowPredicate(permission.rowPredicate());
        vo.setRestrictedColumns(new ArrayList<>(permission.restrictedColumns()));
        vo.setAppliedRuleNames(new ArrayList<>(permission.appliedRuleNames()));
        vo.setEnabledRuleCount(permission.candidateRuleCount());
        return vo;
    }

    private DataPermRule requireExisting(Long id) {
        DataPermRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "权限规则不存在: " + id);
        }
        return rule;
    }

    private DataPermRule toEntity(DataPermRuleDTO dto) {
        String scopeType = dto.getScopeType().trim().toUpperCase();
        String scopeValue = dto.getScopeValue().trim();
        if ("USER".equals(scopeType) && parseLong(scopeValue) == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "按用户命中时，命中对象必须是用户ID");
        }
        if (!"USER".equals(scopeType) && !"ROLE".equals(scopeType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "命中方式只能是 USER 或 ROLE");
        }

        List<String> restricted = normalizeColumns(dto.getRestrictedColumns());
        List<AnalysisQueryDTO.FilterConfig> conditions = normalizeConditions(dto.getConditions());
        if (conditions.isEmpty() && restricted.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "行条件与受限列至少填一项，否则这条规则什么都不做");
        }
        if (!conditions.isEmpty()) {
            proveRenderable(conditions);
        }
        if (dto.getDatasetId() != null) {
            requireDatasetInTenant(dto.getDatasetId());
        }

        DataPermRule entity = new DataPermRule();
        entity.setName(dto.getName().trim());
        entity.setDatasetId(dto.getDatasetId());
        entity.setScopeType(scopeType);
        entity.setScopeValue(scopeValue);
        entity.setConditionsJson(writeJson(conditions));
        entity.setRestrictedColumns(String.join(",", restricted));
        entity.setEnabled(dto.getEnabled() == null ? 1 : (dto.getEnabled() == 0 ? 0 : 1));
        entity.setRemark(trimToNull(dto.getRemark()));
        return entity;
    }

    /** 字段名严格校验（不接受被清洗后通过的脏名字），操作符与值形状交给渲染证明。 */
    private List<AnalysisQueryDTO.FilterConfig> normalizeConditions(List<DataPermRuleDTO.PermConditionDTO> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<AnalysisQueryDTO.FilterConfig> out = new ArrayList<>(source.size());
        for (DataPermRuleDTO.PermConditionDTO item : source) {
            if (item == null) {
                continue;
            }
            String field = SqlBuilder.requireIdentifier(item.getField(), "条件字段");
            String operator = item.getOperator() == null ? "" : item.getOperator().trim();
            if (operator.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "条件字段 " + field + " 缺少操作符");
            }
            Object value = validatePlaceholders(item.getValue(), field);
            AnalysisQueryDTO.FilterConfig condition = new AnalysisQueryDTO.FilterConfig();
            condition.setField(field);
            condition.setOperator(operator);
            condition.setValue(value);
            out.add(condition);
        }
        return out;
    }

    private Object validatePlaceholders(Object value, String field) {
        if (value instanceof String) {
            String text = (String) value;
            if (DataPermissionResolver.isPlaceholder(text)) {
                if (!DataPermissionResolver.isKnownPlaceholder(text)) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + field + " 的占位符 " + text
                            + " 不可用（可选：" + String.join(" / ", DataPermissionResolver.knownPlaceholders()) + "）");
                }
                return PLACEHOLDER_SAMPLE;
            }
            return text;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> out = new ArrayList<>(source.size());
            for (Object item : source) {
                out.add(validatePlaceholders(item, field));
            }
            return out;
        }
        return value;
    }

    private List<String> normalizeColumns(List<String> columns) {
        Set<String> out = new LinkedHashSet<>();
        if (columns == null) {
            return new ArrayList<>(out);
        }
        for (String column : columns) {
            out.add(SqlBuilder.requireIdentifier(column, "受限列"));
        }
        return new ArrayList<>(out);
    }

    /** 现在就过一遍行权限的白名单：存不下或渲染不出规则，绝不留到用户查询时才暴露。 */
    private void proveRenderable(List<AnalysisQueryDTO.FilterConfig> conditions) {
        sqlBuilder.permissionPredicate(conditions);
    }

    private void requireDatasetInTenant(Long datasetId) {
        DatasetMeta meta = modelServiceClient.getDatasetMeta(datasetId);
        if (meta == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数据集不存在，无法给它配规则: " + datasetId);
        }
        String tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null && meta.getTenantId() != null && !tenantId.equals(meta.getTenantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权为其他租户的数据集配置权限规则");
        }
    }

    private DataPermRuleVO toVO(DataPermRule rule) {
        DataPermRuleVO vo = new DataPermRuleVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setDatasetId(rule.getDatasetId());
        vo.setScopeType(rule.getScopeType());
        vo.setScopeValue(rule.getScopeValue());
        vo.setConditions(readConditions(rule));
        vo.setRestrictedColumns(DataPermissionResolver.splitColumns(rule.getRestrictedColumns()));
        vo.setEnabled(rule.getEnabled());
        vo.setRemark(rule.getRemark());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    private List<DataPermRuleDTO.PermConditionDTO> readConditions(DataPermRule rule) {
        String json = rule.getConditionsJson();
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, DataPermRuleDTO.PermConditionDTO.class));
        } catch (Exception e) {
            log.warn("权限规则 {} 的行条件解析失败: {}", rule.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeJson(List<AnalysisQueryDTO.FilterConfig> conditions) {
        if (conditions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(conditions);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "行条件序列化失败");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
