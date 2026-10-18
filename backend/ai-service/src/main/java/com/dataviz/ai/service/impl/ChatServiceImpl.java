package com.dataviz.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.ai.dto.ChatMessageDTO;
import com.dataviz.ai.entity.AiConversation;
import com.dataviz.ai.entity.AiMessage;
import com.dataviz.ai.llm.LlmClient;
import com.dataviz.ai.llm.LlmReply;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 带多少轮历史去问模型：太少没有上下文，太多 token 与延迟都上去了 */
    private static final int HISTORY_TURNS = 10;

    private static final String SYSTEM_PROMPT =
            "你是 DataViz 可视化平台内置的数据分析助手。用中文回答，优先给出可执行的取数口径与图表建议；"
                    + "涉及 SQL 时只给单条 SELECT 语句，不要给出任何写操作；"
                    + "不知道就说不知道，不要编造表名、字段名或数值。";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LlmClient llmClient;

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

        // 先取历史再落本轮提问：顺序反了就会把刚插入的用户消息当成"上一轮"再发一遍
        List<LlmClient.Message> history = loadHistory(conversationId);

        // Save user message
        AiMessage userMessage = AiMessage.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(dto.getContent())
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(userMessage);

        LlmReply reply = llmClient.chat(SYSTEM_PROMPT, history, dto.getContent());
        String assistantResponse = reply.isAvailable()
                ? reply.getText()
                : "【未接入大模型】" + reply.getReason() + "。配置 LLM_API_URL 与 LLM_API_KEY 后重启 ai-service 即可获得真实回答；"
                        + "本轮提问已存库，不会丢。";

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
            if ("New Conversation".equals(conversation.getTitle()) && dto.getContent() != null && dto.getContent().length() <= 50) {
                conversation.setTitle(dto.getContent());
            }
            conversationMapper.updateById(conversation);
        }

        ChatMessageVO vo = toMessageVO(assistantMessage);
        vo.setLlmUsed(reply.isAvailable());
        vo.setLlmNote(reply.isAvailable() ? null : reply.getReason());
        return vo;
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

    /** 取最近若干条按时间正序排列，作为多轮上下文发给模型 */
    private List<LlmClient.Message> loadHistory(Long conversationId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getConversationId, conversationId)
               .orderByDesc(AiMessage::getId)
               .last("LIMIT " + HISTORY_TURNS);
        List<AiMessage> recent = new ArrayList<>(messageMapper.selectList(wrapper));
        Collections.reverse(recent);
        List<LlmClient.Message> turns = new ArrayList<>(recent.size());
        for (AiMessage message : recent) {
            turns.add(new LlmClient.Message(message.getRole(), message.getContent()));
        }
        return turns;
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
