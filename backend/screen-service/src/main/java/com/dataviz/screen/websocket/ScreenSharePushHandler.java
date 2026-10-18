package com.dataviz.screen.websocket;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 分享页的"配置变了"推送通道（单向：服务端 → 客户端），按分享令牌分桶。
 *
 * <p>三条刻意的设计，都是踩过坑之后定的：</p>
 * <ul>
 * <li><b>键是分享令牌，不是 screenId</b>：这条通道免登，客户端手里唯一的东西就是链接里的令牌；
 * 用 screenId 分桶等于要求匿名访客先知道内部主键，而且任何人都能拿 id 订阅别人的大屏。
 * 令牌本身就是"我拿得到这张大屏"的凭证，与免登 HTTP 端点同源。</li>
 * <li><b>只推"变了"，不推内容</b>：一帧几十字节，客户端收到后回去打 {@code /api/screen/share/{token}}
 * 拿全量。推内容要么把整份 config_json 塞进帧（大且重复），要么在前端做增量合并（两套渲染路径必然分叉）。
 * 另外，组件级的实时数据本来就是各渲染器按 refreshInterval 自己轮询的（{@code screen-engine/components/props.ts}），
 * 那条链路不该由这里代劳 —— 这里管的是"大屏被改过/发布/下架/删掉了"。</li>
 * <li><b>不做心跳、不补发</b>：半开连接靠下一次广播发送失败时自清；漏掉的一帧由客户端重连时补拉纠正。
 * 这与免登配置通道同一套取舍，两条通道不该各有各的自愈语义。</li>
 * </ul>
 */
@Slf4j
@Component
public class ScreenSharePushHandler extends TextWebSocketHandler {

    /** 通道路径前缀，握手时从中取出分享令牌作为分桶键 */
    public static final String CHANNEL_PREFIX = "/api/screen/ws/share/";

    private static final String PUSH_ONLY_FRAME = "{\"error\":\"push-only channel\"}";

    /** shareToken -> 该大屏的在线会话。一个令牌多个标签页都保留（不是"后来者踢前者"） */
    private final Map<String, Set<WebSocketSession>> buckets = new ConcurrentHashMap<String, Set<WebSocketSession>>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = resolveToken(session);
        if (token == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        buckets.computeIfAbsent(token, k -> new CopyOnWriteArraySet<WebSocketSession>()).add(session);
        log.info("分享页推送接入: token={}…, 该屏在线会话={}", token.substring(0, Math.min(8, token.length())),
                buckets.get(token).size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端没有可发的指令：回一帧错误，免得有人按"能不能订阅别的大屏"去猜协议
        sendOne(session, PUSH_ONLY_FRAME);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("分享页推送传输错误: sessionId={}, err={}", session.getId(), exception.getMessage());
        remove(session);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 通知某个分享令牌下的全部在线会话"这张大屏变了"。
     *
     * @param reason 变更类型，仅用于日志与客户端提示：updated / unpublished / deleted
     * @return 实际送达的会话数
     */
    public int broadcast(Long screenId, String shareToken, String reason) {
        if (shareToken == null || shareToken.trim().isEmpty()) {
            return 0;
        }
        Set<WebSocketSession> sessions = buckets.get(shareToken);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        Map<String, Object> frame = new LinkedHashMap<String, Object>(4);
        frame.put("screenId", screenId);
        frame.put("reason", reason);
        frame.put("time", System.currentTimeMillis());
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
        log.info("大屏变更已推送: screenId={}, reason={}, 在线会话={}, 实发={}",
                screenId, reason, sessions.size(), delivered);
        return delivered;
    }

    /** 供测试与诊断：当前分桶数 */
    int bucketCount() {
        return buckets.size();
    }

    private String resolveToken(WebSocketSession session) {
        java.net.URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(CHANNEL_PREFIX)) {
            return null;
        }
        String token = path.substring(CHANNEL_PREFIX.length()).trim();
        // 令牌是 UUID hex：一旦带斜杠/问号就说明路径拼错，不给它当订阅键用
        if (token.isEmpty() || token.length() > 64 || token.indexOf('/') >= 0) {
            return null;
        }
        return token;
    }

    private void remove(WebSocketSession session) {
        String token = resolveToken(session);
        if (token == null) {
            return;
        }
        Set<WebSocketSession> sessions = buckets.get(token);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                buckets.remove(token, sessions);
            }
        }
    }

    /** @return 是否发送成功；失败即摘除该会话 */
    private boolean sendOne(WebSocketSession session, String payload) {
        try {
            // WebSocketSession 非线程安全：并发 sendMessage 会抛 IllegalStateException
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.warn("大屏推送失败，已摘除会话: sessionId={}, err={}", session.getId(), e.getMessage());
            remove(session);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("关闭故障会话失败: sessionId={}", session.getId(), e);
        }
    }
}
