package com.dataviz.collab.websocket;

import com.dataviz.common.websocket.handler.BaseWebSocketHandler;
import com.dataviz.common.websocket.util.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class CollabWebSocketHandler extends BaseWebSocketHandler {

    public CollabWebSocketHandler(WebSocketSessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    protected void onOpen(WebSocketSession session, String userId) {
        log.info("Collab WebSocket opened for user: {}", userId);
    }

    @Override
    protected void onClose(WebSocketSession session, String userId, CloseStatus status) {
        log.info("Collab WebSocket closed for user: {}, status: {}", userId, status);
    }

    @Override
    protected void onMessage(WebSocketSession session, String userId, String message) {
        log.debug("Collab message from user {}: {}", userId, message);
        broadcast(message);
    }
}
