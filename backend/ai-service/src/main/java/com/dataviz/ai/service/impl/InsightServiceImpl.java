package com.dataviz.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.ai.dto.InsightGenerateDTO;
import com.dataviz.ai.entity.AiInsight;
import com.dataviz.ai.llm.LlmClient;
import com.dataviz.ai.llm.LlmReply;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    private static final String SYSTEM_PROMPT =
            "你是 DataViz 平台的数据洞察助手。用中文输出结论，先给判断再给依据；"
                    + "没有真实数据支撑时明确说明缺什么，不要编造数值、表名或趋势幅度。";

    /** 出处标记写在 config 里，随洞察一起落库 */
    private static final String LLM_USED_KEY = "llmUsed";
    private static final String LLM_NOTE_KEY = "llmNote";

    private final AiInsightMapper insightMapper;
    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;

    @Override
    public InsightVO generateInsight(InsightGenerateDTO dto) {
        log.info("Generating insight: datasourceId={}, datasetId={}, type={}",
                dto.getDatasourceId(), dto.getDatasetId(), dto.getInsightType());

        LlmReply reply = llmClient.chat(SYSTEM_PROMPT, buildPrompt(dto));
        boolean llmUsed = reply.isAvailable();
        String content = llmUsed ? reply.getText() : generateInsightContent(dto);
        String note = llmUsed ? null : reply.getReason();

        // 出处随 config 落库：洞察是持久化的，事后必须还能分辨哪条是模型写的、哪条是占位模板
        Map<String, Object> config = dto.getConfig() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(dto.getConfig());
        config.put(LLM_USED_KEY, llmUsed);
        if (note != null) {
            config.put(LLM_NOTE_KEY, note);
        }

        AiInsight insight = AiInsight.builder()
                .datasourceId(dto.getDatasourceId())
                .datasetId(dto.getDatasetId())
                .insightType(dto.getInsightType())
                .content(content)
                .config(toJson(config))
                .createTime(LocalDateTime.now())
                .build();
        insightMapper.insert(insight);
        log.info("Generated insight: id={}, type={}, llmUsed={}", insight.getId(), insight.getInsightType(), llmUsed);
        return toVO(insight);
    }

    private String buildPrompt(InsightGenerateDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对下面的分析任务给出数据洞察结论。\n");
        sb.append("洞察类型：").append(dto.getInsightType()).append('\n');
        if (dto.getDatasourceId() != null) {
            sb.append("数据源 ID：").append(dto.getDatasourceId()).append('\n');
        }
        if (dto.getDatasetId() != null) {
            sb.append("数据集 ID：").append(dto.getDatasetId()).append('\n');
        }
        if (dto.getConfig() != null && !dto.getConfig().isEmpty()) {
            sb.append("附加配置：").append(toJson(dto.getConfig())).append('\n');
        }
        sb.append("要求：用中文；只依据给出的信息作答，没有数据就明说缺少哪些数据，不要编造具体数值。");
        return sb.toString();
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

    /**
     * 没接大模型时的兜底文本。
     * <p>刻意不写成"数据呈上升趋势"这类看着像结论的话：那等于凭空造一个分析结果。
     * 这里只说明缺什么、要怎么补，配合 {@code llmUsed=false} 一起交出去。</p>
     */
    private String generateInsightContent(InsightGenerateDTO dto) {
        String type = dto.getInsightType() == null ? "" : dto.getInsightType();
        String requirement;
        switch (type) {
            case "TREND":
                requirement = "需要按时间维度的度量序列（至少两期数据）才能判断趋势。";
                break;
            case "OUTLIER":
                requirement = "需要明细数值列才能做离群点检测（如 3σ 或 IQR）。";
                break;
            case "CORRELATION":
                requirement = "需要两个及以上数值列的成对样本才能计算相关性。";
                break;
            case "DISTRIBUTION":
                requirement = "需要单个数值列的样本分布才能做分箱统计。";
                break;
            default:
                requirement = "未知的洞察类型：" + type + "（支持 TREND / OUTLIER / CORRELATION / DISTRIBUTION）。";
                break;
        }
        return "【未接入大模型，无结论】" + requirement
                + "本轮没有取数、也没有模型参与，因此不给出任何趋势/异常/相关性判断。"
                + "配置 LLM_API_URL 与 LLM_API_KEY 后重新生成即可。";
    }

    private InsightVO toVO(AiInsight insight) {
        InsightVO vo = new InsightVO();
        BeanUtils.copyProperties(insight, vo);
        Map<String, Object> config = new LinkedHashMap<String, Object>(fromJson(insight.getConfig()));
        Object used = config.remove(LLM_USED_KEY);
        Object note = config.remove(LLM_NOTE_KEY);
        vo.setConfig(config);
        // 老数据没有这个标记：null 是"未标注"，既不当 true 也不当 false
        vo.setLlmUsed(used == null ? null : Boolean.valueOf(String.valueOf(used)));
        vo.setLlmNote(note == null ? null : String.valueOf(note));
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
