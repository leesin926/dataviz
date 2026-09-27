package com.dataviz.admin.websocket;

import com.dataviz.common.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 免登全局配置的推送通道（单向：服务端 → 客户端）。
 * <p>
 * 三条刻意的设计取舍：
 * <ul>
 * <li><b>按会话集合而不是按用户建索引</b>：共享的 {@code WebSocketSessionManager} 是
 * {@code userId -> session} 且"同一 userId 后来者踢掉前者"，那对协同编辑对，但对匿名广播通道是错的
 * （同一台机器多个标签页会互相踢线）。这里的键就是 session 本身。</li>
 * <li><b>接入时不下发首帧</b>：初值由客户端启动时打一次免登 HTTP 拿（同一个数据来源、同一个判据），
 * 这里只负责"之后变了"。两处各自决定初值迟早分叉。</li>
 * <li><b>只推 {@code PublicConfigKeys} 白名单</b>：这条通道不带任何凭证就能连，所以它承载的内容
 * 必须与免登 HTTP 端点严格同集 —— 判据由调用方（{@link PublicConfigBroadcaster}）把，
 * 本类不认识业务语义。</li>
 * </ul>
 */
@Slf4j
@Component
public class PublicConfigWebSocketHandler extends TextWebSocketHandler {

    private static final String PUSH_ONLY_FRAME = "{\"error\":\"push-only channel\"}";

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<WebSocketSession>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("免登配置推送通道接入: sessionId={}, 在线会话={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端不需要（也不能）订阅特定 key：回一条错误帧，免得有人按"能不能只订阅某一个键"去猜协议
        sendOne(session, PUSH_ONLY_FRAME);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("免登配置推送通道断开: sessionId={}, status={}, 在线会话={}", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("免登配置推送通道传输错误: sessionId={}, err={}", session.getId(), exception.getMessage());
        sessions.remove(session);
        closeQuietly(session);
    }

    /**
     * 向全部在线会话广播某个键的最新值。
     *
     * @param value 配置值；{@code null} 原样推出去（前端对 null 的既有口径是"保持现状"）
     */
    public void broadcast(String configKey, String value) {
        Map<String, String> frame = new LinkedHashMap<String, String>(4);
        frame.put("key", configKey);
        frame.put("value", value);
        String payload = JsonUtils.toJson(Collections.unmodifiableMap(frame));
        int delivered = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            if (sendOne(session, payload)) {
                delivered++;
            }
        }
        log.info("免登配置已推送: key={}, 在线会话={}, 实发={}", configKey, sessions.size(), delivered);
    }

    /** @return 是否发送成功；失败即摘除该会话（半开连接靠下一次广播自清，不做心跳线程） */
    private boolean sendOne(WebSocketSession session, String payload) {
        try {
            // WebSocketSession 非线程安全：并发 sendMessage 会抛 IllegalStateException
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.warn("免登配置推送失败，已摘除会话: sessionId={}, err={}", session.getId(), e.getMessage());
            sessions.remove(session);
            closeQuietly(session);
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.debug("关闭故障会话失败: sessionId={}", session.getId(), e);
        }
    }
}
