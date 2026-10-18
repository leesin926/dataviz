package com.dataviz.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议（{@code POST /chat/completions}）的大模型客户端。
 *
 * <p>为什么要有这个类：ai-service 的四个能力（问答、NL2SQL、SQL 解释、洞察）此前全部返回
 * 写死的占位文本，而 {@code application.yml} 里的 {@code ai.llm.*} 一个都没被读过 ——
 * 配置项存在但没人用，等于"看起来能配、其实配了也没用"。这里把配置真正接上，
 * 并在没配的时候明说，而不是把占位文本当成 AI 的回答交出去。</p>
 *
 * <p>安全口径：api-key 只出现在请求头里，日志与 {@link #statusNote()} 都不带它；
 * 报错信息里也只保留状态码与响应体摘要，避免把凭据回显给调用方。</p>
 */
@Slf4j
@Component
public class LlmClient {

    /** 脚手架默认值：拿它去发请求只会得到 401，不如一开始就判为"未配置" */
    private static final String PLACEHOLDER_KEY = "your-api-key-here";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public LlmClient(RestTemplateBuilder builder,
                     ObjectMapper objectMapper,
                     @Value("${ai.llm.api-url:}") String apiUrl,
                     @Value("${ai.llm.api-key:}") String apiKey,
                     @Value("${ai.llm.model:}") String model,
                     @Value("${ai.llm.temperature:0.7}") double temperature,
                     @Value("${ai.llm.max-tokens:4096}") int maxTokens,
                     @Value("${ai.llm.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                     @Value("${ai.llm.read-timeout-seconds:60}") int readTimeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = StringUtils.hasText(model) ? model.trim() : "gpt-4";
        this.temperature = temperature;
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
        // RestTemplate 自建而不注入：LLM 的读超时是几十秒量级，和别处的短超时口径不一样，
        // 共用一个 bean 会让"谁拿到的是哪个超时"变成隐式契约
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 5))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds > 0 ? readTimeoutSeconds : 60))
                .build();
    }

    /** 配了地址也配了非占位 key 才算"接上了大模型" */
    public boolean configured() {
        return StringUtils.hasText(apiUrl) && StringUtils.hasText(apiKey) && !PLACEHOLDER_KEY.equals(apiKey);
    }

    /** 给界面显示的一句话状态；只暴露模型名与地址，不暴露 key */
    public String statusNote() {
        if (configured()) {
            return "已接入大模型：" + model + " @ " + apiUrl;
        }
        // 未配置的原因要指准：只缺地址时报"key 是占位值"，值班的人会去改一个本来没错的 key
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(apiUrl)) {
            missing.add("api-url 未设置");
        }
        if (!StringUtils.hasText(apiKey)) {
            missing.add("api-key 为空");
        } else if (PLACEHOLDER_KEY.equals(apiKey)) {
            missing.add("api-key 仍是脚手架占位值");
        }
        return "未配置大模型服务：需要设置 LLM_API_URL 与 LLM_API_KEY（" + joinMissing(missing) + "）";
    }

    private static String joinMissing(List<String> missing) {
        if (missing.isEmpty()) {
            return "配置不完整";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(missing.get(i));
        }
        return sb.toString();
    }

    /** 单轮调用 */
    public LlmReply chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, new ArrayList<Message>(), userPrompt);
    }

    /**
     * 带历史的调用。历史只取 role/content 两个字段，其余一概不往外发。
     */
    public LlmReply chat(String systemPrompt, List<Message> history, String userPrompt) {
        if (!configured()) {
            return LlmReply.unavailable("未配置大模型服务（ai.llm.api-url / ai.llm.api-key）");
        }
        if (!StringUtils.hasText(userPrompt)) {
            return LlmReply.unavailable("提问内容为空");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(message("system", systemPrompt));
        }
        if (history != null) {
            for (Message turn : history) {
                if (turn != null && StringUtils.hasText(turn.getContent())) {
                    messages.add(message(turn.getRole(), turn.getContent()));
                }
            }
        }
        messages.add(message("user", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        try {
            // 取 byte[] 而不是 String：StringHttpMessageConverter 的默认字符集是 ISO-8859-1，
            // 而多数兼容网关回的是不带 charset 的 application/json，中文回答会被拆成乱码
            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                    apiUrl, new HttpEntity<>(body, headers), byte[].class);
            return parse(response.getBody());
        } catch (RestClientResponseException e) {
            // 状态码 + 响应体摘要足够定位（401 是 key、429 是限流、404 是地址写错），不把凭据带出去
            log.warn("大模型调用被拒: status={}, body={}", e.getRawStatusCode(), abbreviate(e.getResponseBodyAsString()));
            return LlmReply.unavailable("大模型服务返回 " + e.getRawStatusCode());
        } catch (RestClientException e) {
            log.warn("大模型调用失败: {}", e.getMessage());
            return LlmReply.unavailable("无法访问大模型服务: " + e.getMessage());
        }
    }

    private LlmReply parse(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return LlmReply.unavailable("大模型返回空响应体");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                String err = root.path("error").path("message").asText("");
                return LlmReply.unavailable(StringUtils.hasText(err) ? "大模型返回错误: " + err : "大模型响应里没有 choices");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                // 有些兼容实现把文本放在 text 字段
                content = choices.get(0).path("text");
            }
            String text = content.asText("").trim();
            if (!StringUtils.hasText(text)) {
                return LlmReply.unavailable("大模型返回了空内容");
            }
            return LlmReply.of(text);
        } catch (Exception e) {
            log.warn("大模型响应解析失败: {}, body={}", e.getMessage(),
                    abbreviate(new String(responseBody, StandardCharsets.UTF_8)));
            return LlmReply.unavailable("大模型响应不是可解析的 JSON");
        }
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", normalizeRole(role));
        m.put("content", content);
        return m;
    }

    /** 只放行协议里的三种角色，别把库里存的奇怪值原样发出去 */
    private static String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        String r = role.trim().toLowerCase();
        if ("assistant".equals(r)) {
            return "assistant";
        }
        if ("system".equals(r)) {
            return "system";
        }
        return "user";
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    /** 一轮对话消息 */
    public static final class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
