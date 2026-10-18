package com.dataviz.ai.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    private Long id;

    private Long conversationId;

    /**
     * USER / ASSISTANT / SYSTEM
     */
    private String role;

    private String content;

    private String sqlGenerated;

    private Integer tokenCount;

    private LocalDateTime createTime;

    /**
     * 本条 ASSISTANT 回复是否真由大模型生成。
     * <p>只有刚生成的那条会带值；查历史时库里没有这个维度，返回 null 表示"未标注"，
     * 界面不能把 null 当成 false，也不能当成 true。</p>
     */
    private Boolean llmUsed;

    private String llmNote;
}
