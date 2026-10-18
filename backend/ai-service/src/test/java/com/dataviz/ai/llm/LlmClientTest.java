package com.dataviz.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmClient 的两条底线要用测试钉住：
 * <b>没配置</b>时不能发出任何请求、也不能把占位文本粉饰成模型回答；
 * <b>配置了</b>时 api-key 只能出现在请求头，绝不能落到请求体或状态文案里。
 * 大模型地址用桩工厂替掉，所以这里不需要真凭据，也不需要联网。
 */
class LlmClientTest {

    private static final String CHAT_URL = "https://llm.example.com/v1/chat/completions";
    private static final String SECRET = "sk-test-secret-DO-NOT-LEAK";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockClientHttpRequest lastRequest;

    @Test
    @DisplayName("地址为空即判未配置，并且不发请求")
    void should_notCallHttp_whenUrlMissing() {
        LlmClient client = client("", SECRET, returning(200, okBody("不该被用到")));

        assertFalse(client.configured());
        assertTrue(client.statusNote().contains("未配置"));
        // key 是配的、缺的只有地址：状态要指准 api-url，不能反过来说 key 是占位值
        assertTrue(client.statusNote().contains("api-url"), client.statusNote());
        assertFalse(client.statusNote().contains("占位"), client.statusNote());

        LlmReply reply = client.chat("sys", "问题");
        assertFalse(reply.isAvailable());
        assertTrue(reply.getReason().contains("未配置大模型服务"));
        assertNull(lastRequest, "未配置时不应发出 HTTP 请求");
    }

    @Test
    @DisplayName("脚手架占位 key 等同于没配置：拿它发请求只会换来 401")
    void should_treatPlaceholderKeyAsUnconfigured() {
        LlmClient client = client(CHAT_URL, "your-api-key-here", returning(200, okBody("x")));

        assertFalse(client.configured());
        assertTrue(client.statusNote().contains("占位"), client.statusNote());
        assertFalse(client.statusNote().contains("api-url"), client.statusNote());
        assertNull(client.chat("sys", "问题").getText());
        assertNull(lastRequest);
    }

    @Test
    @DisplayName("地址与 key 都缺时两个都要报出来")
    void should_listEveryMissingItem() {
        LlmClient client = client("", "", returning(200, okBody("x")));

        assertTrue(client.statusNote().contains("api-url"), client.statusNote());
        assertTrue(client.statusNote().contains("api-key 为空"), client.statusNote());
    }

    @Test
    @DisplayName("状态文案只说模型与地址，不带上 key")
    void should_notLeakKeyInStatusNote() {
        LlmClient client = client(CHAT_URL, SECRET, returning(200, okBody("x")));

        assertTrue(client.configured());
        assertEquals("已接入大模型：qwen-max @ " + CHAT_URL, client.statusNote());
        assertFalse(client.statusNote().contains(SECRET));
    }

    @Test
    @DisplayName("配置齐了但提问为空时先拦下，不烧一次调用")
    void should_rejectBlankQuestion_beforeCalling() {
        LlmClient client = client(CHAT_URL, SECRET, returning(200, okBody("x")));

        LlmReply reply = client.chat("sys", "   ");
        assertFalse(reply.isAvailable());
        assertTrue(reply.getReason().contains("提问内容为空"));
        assertNull(lastRequest);
    }

    @Test
    @DisplayName("正常应答：请求头带 Bearer、请求体是 OpenAI 协议形状、内容取回")
    void should_sendOpenAiShapeAndParseContent() throws Exception {
        LlmClient client = client(CHAT_URL, SECRET, returning(200, okBody("  近 7 天销售额上升  ")));

        LlmReply reply = client.chat("你是 SQL 专家",
                Arrays.asList(new LlmClient.Message("HISTORY", "上一轮"), new LlmClient.Message("assistant", "上一答")),
                "近 7 天销售额");

        assertTrue(reply.isAvailable());
        assertEquals("近 7 天销售额上升", reply.getText());

        assertNotNull(lastRequest);
        assertEquals(HttpMethod.POST, lastRequest.getMethod());
        assertEquals(URI.create(CHAT_URL), lastRequest.getURI());
        assertEquals("Bearer " + SECRET, lastRequest.getHeaders().getFirst("Authorization"));

        String body = lastRequest.getBodyAsString();
        assertFalse(body.contains(SECRET), "api-key 只能待在请求头里");
        assertEquals("qwen-max", json(body).path("model").asText());
        assertEquals(0.2, json(body).path("temperature").asDouble(), 0.0001);
        assertEquals(512, json(body).path("max_tokens").asInt());

        assertEquals(4, json(body).path("messages").size());
        assertEquals("system", json(body).path("messages").get(0).path("role").asText());
        // 库里的角色值不可信，只放行 system/assistant/user 三种，其余归一为 user
        assertEquals("user", json(body).path("messages").get(1).path("role").asText());
        assertEquals("assistant", json(body).path("messages").get(2).path("role").asText());
        assertEquals("近 7 天销售额", json(body).path("messages").get(3).path("content").asText());
    }

    @Test
    @DisplayName("兼容实现把文本放在 choices[0].text 时也要取到")
    void should_fallbackToTextField() {
        LlmClient client = client(CHAT_URL, SECRET,
                returning(200, "{\"choices\":[{\"text\":\"纯文本应答\"}]}"));

        assertEquals("纯文本应答", client.chat("sys", "问题").getText());
    }

    @Test
    @DisplayName("响应体为空 / 不是 JSON / 没有 choices 分别给出可区分的原因")
    void should_distinguishBadResponses() {
        assertTrue(client(CHAT_URL, SECRET, returning(200, "")).chat("sys", "q").getReason().contains("空响应体"));
        assertTrue(client(CHAT_URL, SECRET, returning(200, "not-json")).chat("sys", "q").getReason().contains("不是可解析的 JSON"));
        assertTrue(client(CHAT_URL, SECRET, returning(200, "{}")).chat("sys", "q").getReason().contains("没有 choices"));
        assertTrue(client(CHAT_URL, SECRET, returning(200, "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"))
                .chat("sys", "q").getReason().contains("空内容"));
    }

    @Test
    @DisplayName("被拒（401）时报状态码，不回显凭据")
    void should_reportStatusWithoutKey_whenRejected() {
        LlmClient client = client(CHAT_URL, SECRET, returning(401, "{\"error\":{\"message\":\"invalid api key\"}}"));

        LlmReply reply = client.chat("sys", "q");
        assertFalse(reply.isAvailable());
        assertEquals("大模型服务返回 401", reply.getReason());
        assertFalse(reply.getReason().contains(SECRET));
    }

    @Test
    @DisplayName("服务侧带 error.message 时优先把原因带给调用方")
    void should_preferErrorNodeMessage() {
        LlmClient client = client(CHAT_URL, SECRET, returning(200, "{\"error\":{\"message\":\"rate limit exceeded\"}}"));

        assertTrue(client.chat("sys", "q").getReason().contains("rate limit exceeded"));
    }

    @Test
    @DisplayName("连不上与拿到错误响应要分开说：一个是部署/网络，一个是服务侧拒绝")
    void should_reportConnectFailureSeparately() {
        LlmClient client = client(CHAT_URL, SECRET, failing());

        LlmReply reply = client.chat("sys", "q");
        assertFalse(reply.isAvailable());
        assertTrue(reply.getReason().startsWith("无法访问大模型服务"));
    }

    private LlmClient client(String apiUrl, String apiKey, ClientHttpRequestFactory factory) {
        return new LlmClient(new RestTemplateBuilder().requestFactory(() -> factory), objectMapper,
                apiUrl, apiKey, "qwen-max", 0.2, 512, 5, 60);
    }

    private static String okBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    private com.fasterxml.jackson.databind.JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    /**
     * 桩工厂。继承 SimpleClientHttpRequestFactory 不是为了发请求，而是因为
     * {@code RestTemplateBuilder.setConnectTimeout/setReadTimeout} 会按工厂类型找 setter，
     * 匿名的裸 ClientHttpRequestFactory 会在 build() 阶段直接抛 IllegalStateException。
     */
    private final class StubFactory extends SimpleClientHttpRequestFactory {
        private final int status;
        private final String body;
        private final boolean fail;

        private StubFactory(int status, String body, boolean fail) {
            this.status = status;
            this.body = body;
            this.fail = fail;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            if (fail) {
                throw new ResourceAccessException("connect timed out");
            }
            MockClientHttpRequest request = new MockClientHttpRequest(httpMethod, uri);
            request.setResponse(new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8),
                    HttpStatus.valueOf(status)));
            lastRequest = request;
            return request;
        }
    }

    private ClientHttpRequestFactory returning(int status, String body) {
        return new StubFactory(status, body, false);
    }

    private ClientHttpRequestFactory failing() {
        return new StubFactory(0, null, true);
    }
}
