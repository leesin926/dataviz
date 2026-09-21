package com.dataviz.ai.controller;

import com.dataviz.ai.dto.ChatMessageDTO;
import com.dataviz.ai.service.ChatService;
import com.dataviz.ai.vo.ChatMessageVO;
import com.dataviz.ai.vo.ConversationVO;
import com.dataviz.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/conversation")
    public R<ConversationVO> createConversation(@RequestParam(required = false) String title) {
        return R.ok(chatService.createConversation(title));
    }

    @PostMapping("/send")
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
    public R<Void> deleteConversation(@PathVariable Long conversationId) {
        chatService.deleteConversation(conversationId);
        return R.ok();
    }
}
