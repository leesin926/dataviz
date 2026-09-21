package com.dataviz.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.ai.dto.InsightGenerateDTO;
import com.dataviz.ai.entity.AiInsight;
import com.dataviz.ai.mapper.AiInsightMapper;
import com.dataviz.ai.service.InsightService;
import com.dataviz.ai.vo.InsightVO;
import com.dataviz.common.core.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    private final AiInsightMapper insightMapper;
    private final ObjectMapper objectMapper;

    @Override
    public InsightVO generateInsight(InsightGenerateDTO dto) {
        log.info("Generating insight: datasourceId={}, datasetId={}, type={}",
                dto.getDatasourceId(), dto.getDatasetId(), dto.getInsightType());

        // TODO: Implement actual insight generation logic
        // Steps:
        // 1. Fetch data from the datasource/dataset
        // 2. Apply statistical analysis based on insightType
        // 3. Generate insight content using AI/ML
        String content = generateInsightContent(dto);

        AiInsight insight = AiInsight.builder()
                .datasourceId(dto.getDatasourceId())
                .datasetId(dto.getDatasetId())
                .insightType(dto.getInsightType())
                .content(content)
                .config(toJson(dto.getConfig()))
                .createTime(LocalDateTime.now())
                .build();
        insightMapper.insert(insight);
        log.info("Generated insight: id={}, type={}", insight.getId(), insight.getInsightType());
        return toVO(insight);
    }

    @Override
    public List<InsightVO> getInsights(Long datasourceId, Long datasetId, String insightType) {
        LambdaQueryWrapper<AiInsight> wrapper = new LambdaQueryWrapper<>();
        if (datasourceId != null) {
            wrapper.eq(AiInsight::getDatasourceId, datasourceId);
        }
        if (datasetId != null) {
            wrapper.eq(AiInsight::getDatasetId, datasetId);
        }
        if (StringUtils.hasText(insightType)) {
            wrapper.eq(AiInsight::getInsightType, insightType);
        }
        wrapper.orderByDesc(AiInsight::getCreateTime);
        return insightMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public InsightVO getInsightDetail(Long id) {
        AiInsight insight = insightMapper.selectById(id);
        if (insight == null) {
            throw new BizException("Insight not found: " + id);
        }
        return toVO(insight);
    }

    private String generateInsightContent(InsightGenerateDTO dto) {
        String result;
        switch (dto.getInsightType()) {
            case "TREND":
                result = "Trend analysis placeholder: Data shows a general upward trend over the analyzed period.";
                break;
            case "OUTLIER":
                result = "Outlier detection placeholder: 3 data points were identified as statistical outliers.";
                break;
            case "CORRELATION":
                result = "Correlation analysis placeholder: Strong positive correlation detected between variables A and B.";
                break;
            case "DISTRIBUTION":
                result = "Distribution analysis placeholder: Data follows a roughly normal distribution with slight right skew.";
                break;
            default:
                result = "Unknown insight type: " + dto.getInsightType();
                break;
        }
        return result;
    }

    private InsightVO toVO(AiInsight insight) {
        InsightVO vo = new InsightVO();
        BeanUtils.copyProperties(insight, vo);
        vo.setConfig(fromJson(insight.getConfig()));
        return vo;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config", e);
            return null;
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize config: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
