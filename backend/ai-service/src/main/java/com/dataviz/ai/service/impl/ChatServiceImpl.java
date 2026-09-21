package com.dataviz.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.ai.dto.ChatMessageDTO;
import com.dataviz.ai.entity.AiConversation;
import com.dataviz.ai.entity.AiMessage;
import com.dataviz.ai.mapper.AiConversationMapper;
import com.dataviz.ai.mapper.AiMessageMapper;
import com.dataviz.ai.service.ChatService;
import com.dataviz.ai.vo.ChatMessageVO;
import com.dataviz.ai.vo.ConversationVO;
import com.dataviz.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    @Override
    @Transactional
    public ConversationVO createConversation(String title) {
        AiConversation conversation = AiConversation.builder()
                .title(title != null ? title : "New Conversation")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(conversation);
        log.info("Created conversation: id={}, title={}", conversation.getId(), conversation.getTitle());
        return toConversationVO(conversation);
    }

    @Override
    @Transactional
    public ChatMessageVO sendMessage(ChatMessageDTO dto) {
        Long conversationId = dto.getConversationId();

        // Create conversation if not provided
        if (conversationId == null) {
            AiConversation conversation = AiConversation.builder()
                    .title("New Conversation")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            conversationMapper.insert(conversation);
            conversationId = conversation.getId();
        }

        // Save user message
        AiMessage userMessage = AiMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(dto.getContent())
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(userMessage);

        // Call LLM to generate response
        // TODO: Integrate actual LLM API call (OpenAI, etc.)
        String assistantResponse = callLlm(dto.getContent());

        // Save assistant message
        AiMessage assistantMessage = AiMessage.builder()
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content(assistantResponse)
                .tokenCount(estimateTokens(assistantResponse))
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(assistantMessage);

        // Update conversation timestamp
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setUpdateTime(LocalDateTime.now());
            // Update title with first user message if it's still default
            if ("New Conversation".equals(conversation.getTitle()) && dto.getContent().length() <= 50) {
                conversation.setTitle(dto.getContent());
            }
            conversationMapper.updateById(conversation);
        }

        return toMessageVO(assistantMessage);
    }

    @Override
    public List<ChatMessageVO> getHistory(Long conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
               .orderByAsc(AiMessage::getCreateTime);
        return messageMapper.selectList(wrapper).stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConversationVO> listConversations() {
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiConversation::getUpdateTime);
        return conversationMapper.selectList(wrapper).stream()
                .map(this::toConversationVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException("Conversation not found: " + conversationId);
        }
        // Delete all messages in the conversation
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
        conversationMapper.deleteById(conversationId);
        log.info("Deleted conversation: id={}", conversationId);
    }

    /**
     * Call LLM API to generate response.
     * TODO: Replace with actual LLM integration (OpenAI, etc.)
     */
    private String callLlm(String userMessage) {
        log.info("Calling LLM with message: {}", userMessage);
        // Placeholder response - in production, call the configured LLM API
        return "I've received your message: \"" + userMessage + "\". " +
               "This is a placeholder response. LLM integration is pending configuration.";
    }

    private int estimateTokens(String text) {
        // Rough estimate: 1 token ~ 4 characters
        return text != null ? text.length() / 4 : 0;
    }

    private ConversationVO toConversationVO(AiConversation conversation) {
        ConversationVO vo = new ConversationVO();
        BeanUtils.copyProperties(conversation, vo);
        return vo;
    }

    private ChatMessageVO toMessageVO(AiMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}
