package com.dataviz.admin.websocket;

import com.dataviz.admin.config.PublicConfigKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 免登全局配置推送通道的握手注册。
 * <p>
 * 刻意<b>不</b>复用 {@code common-websocket}：那份共享 {@code WebSocketConfig} 绑的是 {@code /ws/**}，
 * 且构造参数要一个 {@code BaseWebSocketHandler} bean —— admin-service 的 {@code @ComponentScan} 里含
 * {@code com.dataviz.common}，一旦依赖那个模块就会因为"没有 BaseWebSocketHandler"直接启动失败，
 * 顺带把协同编辑那套按 userId 互踢的会话语义引进来。这里只要一个匿名广播通道。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class PublicConfigWebSocketConfig implements WebSocketConfigurer {

    private final PublicConfigWebSocketHandler publicConfigWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 通配来源是安全的，因为这条通道只回传免登 HTTP 白名单里本就能读到的键值，且不接受客户端指令
        registry.addHandler(publicConfigWebSocketHandler, PublicConfigKeys.PUSH_CHANNEL_PATH)
                .setAllowedOriginPatterns("*");
    }
}
