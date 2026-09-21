package com.dataviz.common.websocket.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session Manager with thread-safe session storage
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** userId -> WebSocketSession mapping */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * Add a session
     */
    public void addSession(String userId, WebSocketSession session) {
        WebSocketSession old = sessions.put(userId, session);
        if (old != null && old.isOpen()) {
            try {
                old.close();
            } catch (IOException e) {
                log.warn("Failed to close old session for userId={}", userId, e);
            }
        }
        log.debug("Session added: userId={}, totalSessions={}", userId, sessions.size());
    }

    /**
     * Remove a session
     */
    public void removeSession(String userId) {
        sessions.remove(userId);
        log.debug("Session removed: userId={}, totalSessions={}", userId, sessions.size());
    }

    /**
     * Get session by userId
     */
    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    /**
     * Get current online user count
     */
    public int getOnlineCount() {
        return sessions.size();
    }

    /**
     * Get all online user IDs
     */
    public Set<String> getAllUserIds() {
        return sessions.keySet();
    }

    /**
     * Broadcast a text message to all connected sessions
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.forEach((userId, session) -> {
            if (session.isOpen()) {
                sendMessage(session, userId, textMessage);
            }
        });
    }

    /**
     * Send a text message to a specific user
     */
    public boolean sendToUser(String userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            sendMessage(session, userId, new TextMessage(message));
            return true;
        }
        log.warn("Cannot send to userId={}: session not found or closed", userId);
        return false;
    }

    /**
     * Send to multiple users
     */
    public void sendToUsers(Set<String> userIds, String message) {
        userIds.forEach(userId -> sendToUser(userId, message));
    }

    private void sendMessage(WebSocketSession session, String userId, TextMessage message) {
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.error("Failed to send message to userId={}", userId, e);
        }
    }
}
