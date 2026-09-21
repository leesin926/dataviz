package com.dataviz.ai.dto;

import lombok.Data;

@Data
public class ChatMessageDTO {

    /**
     * Conversation ID (required if continuing an existing conversation)
     */
    private Long conversationId;

    /**
     * User message content
     */
    private String content;
}
