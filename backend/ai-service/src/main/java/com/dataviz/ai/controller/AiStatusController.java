package com.dataviz.ai.controller;

import com.dataviz.ai.llm.LlmClient;
import com.dataviz.ai.vo.AiStatusVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力接入状态。
 * <p>只读，走 {@code ai:read}；返回内容里不含 api-key，只有模型名与地址，
 * 便于运维在界面上直接确认"这台环境到底接没接大模型"。</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@RequiresPermission("ai:read")
public class AiStatusController {

    private final LlmClient llmClient;

    @Value("${ai.llm.model:}")
    private String model;

    @GetMapping("/status")
    public R<AiStatusVO> status() {
        return R.ok(AiStatusVO.builder()
                .llmConfigured(llmClient.configured())
                .note(llmClient.statusNote())
                .model(llmClient.configured() ? model : null)
                .build());
    }
}
