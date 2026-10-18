package com.dataviz.screen.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 分享页变更推送通道的握手注册。
 * <p>刻意不复用 {@code common-websocket} 的那份 {@code WebSocketConfig}：它按 {@code userId} 建索引，
 * 且"同一 userId 后来者踢掉前者" —— 对协同编辑对，对匿名广播是错的（同一台机器多个标签页会互相踢线）。
 * 两条路径也不冲突：那份注册的是 {@code /ws/**}，这里挂的是 {@code /api/screen/ws/share/*}，
 * 后者落在网关既有的 {@code Path=/api/screen/**} 路由里，穿网关不需要新增路由条目。</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ScreenSharePushConfig implements WebSocketConfigurer {

    private final ScreenSharePushHandler screenSharePushHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 通配来源是安全的：这条通道只说"这张大屏变了"，内容仍要客户端用自己手里的令牌去免登端点换
        registry.addHandler(screenSharePushHandler, ScreenSharePushHandler.CHANNEL_PREFIX + "*")
                .setAllowedOriginPatterns("*");
    }
}
