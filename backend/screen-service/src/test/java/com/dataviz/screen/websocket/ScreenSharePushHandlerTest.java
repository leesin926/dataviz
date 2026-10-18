package com.dataviz.screen.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 分享页变更推送通道的分桶行为。
 * <p>这里守的是三条会让线上出怪事的地方：跨屏串帧、同一大屏的多个标签页互相踢线、半开连接赖在桶里不走。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScreenSharePushHandlerTest {

    private static final String TOKEN_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOKEN_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private final ScreenSharePushHandler handler = new ScreenSharePushHandler();

    private WebSocketSession connected(String path, String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost:8091" + path));
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("按令牌分桶 - 只送达这一张大屏的订阅者")
    void should_deliverOnlyToMatchingToken_when_broadcasting() throws IOException {
        WebSocketSession a = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_A, "s-a");
        WebSocketSession b = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_B, "s-b");
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);

        int delivered = handler.broadcast(1L, TOKEN_A, "updated");

        assertThat(delivered).isEqualTo(1);
        verify(a, times(1)).sendMessage(any(TextMessage.class));
        verify(b, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("同一大屏多个标签页都保留，不做后来者踢前者")
    void should_keepEverySession_when_sameTokenConnectsTwice() throws IOException {
        WebSocketSession first = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_A, "s-1");
        WebSocketSession second = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_A, "s-2");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        assertThat(handler.broadcast(7L, TOKEN_A, "updated")).isEqualTo(2);
        verify(first).sendMessage(any(TextMessage.class));
        verify(second).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("发送失败即摘除 - 下一次广播不再打在死连接上")
    void should_dropSession_when_sendFails() throws IOException {
        WebSocketSession dead = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_A, "s-dead");
        doThrow(new IOException("broken pipe")).when(dead).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(dead);

        assertThat(handler.broadcast(1L, TOKEN_A, "updated")).isZero();
        assertThat(handler.broadcast(1L, TOKEN_A, "updated")).isZero();
        verify(dead, times(1)).sendMessage(any(TextMessage.class));
        verify(dead).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("路径不是分享通道 - 握手直接拒绝，不给它建桶")
    void should_rejectHandshake_when_pathIsNotShareChannel() throws IOException {
        WebSocketSession stranger = connected("/ws/screen/123", "s-x");

        handler.afterConnectionEstablished(stranger);

        assertThat(handler.bucketCount()).isZero();
        verify(stranger).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("断开后从桶里摘掉 - 桶空了整条键一起消失")
    void should_releaseBucket_when_lastSessionCloses() {
        WebSocketSession session = connected(ScreenSharePushHandler.CHANNEL_PREFIX + TOKEN_A, "s-a");
        handler.afterConnectionEstablished(session);
        assertThat(handler.bucketCount()).isEqualTo(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(handler.bucketCount()).isZero();
        assertThat(handler.broadcast(1L, TOKEN_A, "updated")).isZero();
    }

    @Test
    @DisplayName("没有订阅者或令牌为空 - 什么都不做，也不抛")
    void should_noOp_when_nobodyIsListening() {
        assertThat(handler.broadcast(1L, null, "updated")).isZero();
        assertThat(handler.broadcast(1L, "  ", "updated")).isZero();
        assertThat(handler.broadcast(1L, TOKEN_B, "updated")).isZero();
    }
}
