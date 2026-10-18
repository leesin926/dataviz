package com.dataviz.ai.vo;

import lombok.Builder;
import lombok.Data;

/**
 * AI 能力的接入状态。
 *
 * <p>界面靠它决定要不要挂"未接入大模型"的横幅。之所以要单独给一个状态端点：
 * 聊天历史、洞察列表都是<b>过去</b>生成的，用当前配置去推断它们的出处会得出错误结论，
 * 而"当前能不能真答"这件事必须能单独问一次。</p>
 */
@Data
@Builder
public class AiStatusVO {

    /** 配了地址与非占位 key 才为 true */
    private boolean llmConfigured;

    /** 给人看的一句话说明，不含 api-key */
    private String note;

    private String model;
}
