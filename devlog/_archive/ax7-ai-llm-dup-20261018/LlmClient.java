package com.dataviz.ai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    @Value("${ai.llm.api-url}")
    private String apiUrl;

    @Value("${ai.llm.api-key}")
    private String apiKey;

    @Value("${ai.llm.model}")
    private String model;

    @Value("${ai.llm.max-tokens}")
    private int maxTokens;

    @Value("${ai.llm.temperature}")
    private double temperature;

    private final WebClient webClient = WebClient.builder().build();

    public String chat(String systemPrompt, String userMessage) {
        log.info("Calling LLM API: model={}, apiUrl={}", model, apiUrl);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        
        List<Map<String, String>> messages = Arrays.asList(systemMsg, userMsg);
        requestBody.put("messages", messages);

        try {
            Map<String, Object> response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            log.warn("LLM response did not contain expected structure");
            return "";
        } catch (Exception e) {
            log.error("Failed to call LLM API: {}", e.getMessage(), e);
            throw new RuntimeException("LLM API call failed", e);
        }
    }
}
