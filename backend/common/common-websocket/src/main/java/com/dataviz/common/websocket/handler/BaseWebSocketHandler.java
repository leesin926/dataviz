package com.dataviz.common.websocket.handler;

import com.dataviz.common.websocket.util.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;

/**
 * Abstract base WebSocket handler with session management and template-method hooks.
 * Subclasses implement onOpen, onClose, onMessage for custom logic.
 */
public abstract class BaseWebSocketHandler extends TextWebSocketHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final WebSocketSessionManager sessionManager;

    protected BaseWebSocketHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        sessionManager.addSession(userId, session);
        log.info("WebSocket connected: userId={}, sessionId={}, onlineCount={}",
                userId, session.getId(), sessionManager.getOnlineCount());
        onOpen(session, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = extractUserId(session);
        String payload = message.getPayload();
        log.debug("WebSocket message received: userId={}, payload={}", userId, payload);
        onMessage(session, userId, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        sessionManager.removeSession(userId);
        log.info("WebSocket closed: userId={}, sessionId={}, status={}, onlineCount={}",
                userId, session.getId(), status, sessionManager.getOnlineCount());
        onClose(session, userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Broadcast a message to all connected sessions
     */
    public void broadcast(String message) {
        sessionManager.broadcast(message);
    }

    /**
     * Send a message to a specific user
     */
    public boolean sendToUser(String userId, String message) {
        return sessionManager.sendToUser(userId, message);
    }

    /**
     * Get all online user IDs
     */
    public Set<String> getOnlineUsers() {
        return sessionManager.getAllUserIds();
    }

    /**
     * Called when a new connection is established
     */
    protected abstract void onOpen(WebSocketSession session, String userId);

    /**
     * Called when a connection is closed
     */
    protected abstract void onClose(WebSocketSession session, String userId, CloseStatus status);

    /**
     * Called when a text message is received
     */
    protected abstract void onMessage(WebSocketSession session, String userId, String message);

    private String extractUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId != null ? userId.toString() : session.getId();
    }
}
