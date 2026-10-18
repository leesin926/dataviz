package com.dataviz.screen.websocket;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ServerEndpoint("/ws/screen/{screenId}")
public class ScreenWebSocketEndpoint {

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger CONNECTION_COUNT = new AtomicInteger(0);

    @OnOpen
    public void onOpen(Session session, @PathParam("screenId") String screenId) {
        SESSIONS.put(session.getId(), session);
        CONNECTION_COUNT.incrementAndGet();
        log.info("WebSocket opened: screenId={}, sessionId={}, totalConnections={}", 
                screenId, session.getId(), CONNECTION_COUNT.get());
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("screenId") String screenId) {
        log.info("Received message from screen {}: {}", screenId, message);
        broadcastToScreen(screenId, message);
    }

    @OnClose
    public void onClose(Session session, @PathParam("screenId") String screenId) {
        SESSIONS.remove(session.getId());
        CONNECTION_COUNT.decrementAndGet();
        log.info("WebSocket closed: screenId={}, sessionId={}, totalConnections={}", 
                screenId, session.getId(), CONNECTION_COUNT.get());
    }

    @OnError
    public void onError(Session session, Throwable throwable, @PathParam("screenId") String screenId) {
        log.error("WebSocket error: screenId={}, sessionId={}", screenId, session.getId(), throwable);
        SESSIONS.remove(session.getId());
        CONNECTION_COUNT.decrementAndGet();
    }

    private void broadcastToScreen(String screenId, String message) {
        SESSIONS.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("Failed to send message to session: {}", session.getId(), e);
                }
            }
        });
    }

    public void pushData(String screenId, String data) {
        broadcastToScreen(screenId, data);
    }
}
