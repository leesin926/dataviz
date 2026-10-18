package com.dataviz.ai.controller;

import com.dataviz.ai.dto.ChatMessageDTO;
import com.dataviz.ai.service.ChatService;
import com.dataviz.ai.vo.ChatMessageVO;
import com.dataviz.ai.vo.ConversationVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 助手会话。
 * <p>读档 = 看历史/会话列表；写档 = 任何会落库或会调大模型的动作。
 * 把"调模型"划进写档是因为它有真实成本（token 计费），两档粒度下只能挂在更严的那一档上。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
@RequiresPermission("ai:read")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/conversation")
    @RequiresPermission("ai:write")
    public R<ConversationVO> createConversation(@RequestParam(required = false) String title) {
        return R.ok(chatService.createConversation(title));
    }

    @PostMapping("/send")
    @RequiresPermission("ai:write")
    public R<ChatMessageVO> sendMessage(@RequestBody ChatMessageDTO dto) {
        return R.ok(chatService.sendMessage(dto));
    }

    @GetMapping("/history/{conversationId}")
    public R<List<ChatMessageVO>> getHistory(@PathVariable Long conversationId) {
        return R.ok(chatService.getHistory(conversationId));
    }

    @GetMapping("/conversations")
    public R<List<ConversationVO>> listConversations() {
        return R.ok(chatService.listConversations());
    }

    @DeleteMapping("/conversation/{conversationId}")
    @RequiresPermission("ai:write")
    public R<Void> deleteConversation(@PathVariable Long conversationId) {
        chatService.deleteConversation(conversationId);
        return R.ok();
    }
}
