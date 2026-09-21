package com.dataviz.ai.service;

import com.dataviz.ai.dto.ChatMessageDTO;
import com.dataviz.ai.vo.ChatMessageVO;
import com.dataviz.ai.vo.ConversationVO;

import java.util.List;

/**
 * Manages AI conversations and messages, calls LLM
 */
public interface ChatService {

    /**
     * Create a new conversation
     */
    ConversationVO createConversation(String title);

    /**
     * Send a message and get AI response
     */
    ChatMessageVO sendMessage(ChatMessageDTO dto);

    /**
     * Get conversation message history
     */
    List<ChatMessageVO> getHistory(Long conversationId);

    /**
     * Get all conversations for current user
     */
    List<ConversationVO> listConversations();

    /**
     * Delete a conversation
     */
    void deleteConversation(Long conversationId);
}
