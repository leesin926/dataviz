package com.dataviz.common.websocket.config;

import com.dataviz.common.websocket.handler.BaseWebSocketHandler;
import com.dataviz.common.websocket.interceptor.WebSocketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration with SockJS fallback support
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BaseWebSocketHandler webSocketHandler;
    private final WebSocketInterceptor webSocketInterceptor;

    public WebSocketConfig(BaseWebSocketHandler webSocketHandler, WebSocketInterceptor webSocketInterceptor) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketInterceptor = webSocketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/**")
                .addInterceptors(webSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
