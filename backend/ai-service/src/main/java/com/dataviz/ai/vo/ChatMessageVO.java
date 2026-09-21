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
}
