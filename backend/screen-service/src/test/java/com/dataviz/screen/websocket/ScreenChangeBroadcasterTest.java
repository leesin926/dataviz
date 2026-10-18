package com.dataviz.screen.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 推送时机：只在提交之后推。
 * <p>客户端收到帧就重拉配置，所以"回滚了但已经推出去"会把访客刷成一份库里不存在的配置，
 * 且没有轮询兜底 ⇒ 这个错误状态不会自愈。这三条用例守的就是这个。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScreenChangeBroadcasterTest {

    private static final String TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 模拟事务上下文：Spring 在进入 @Transactional 时就是这么打开 synchronization 的 */
    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("事务进行中 - 当场不推，提交那一刻才推一次")
    void should_deferPush_until_commit() {
        ScreenSharePushHandler pushHandler = mock(ScreenSharePushHandler.class);
        ScreenChangeBroadcaster broadcaster = new ScreenChangeBroadcaster(pushHandler);
        beginTransaction();

        broadcaster.publishAfterCommit(1L, TOKEN, "updated");
        verify(pushHandler, never()).broadcast(anyLong(), anyString(), anyString());

        commit();
        verify(pushHandler, times(1)).broadcast(1L, TOKEN, "updated");
    }

    @Test
    @DisplayName("事务回滚（never commit）- 一帧都不发，访客保持旧配置")
    void should_notPushAtAll_when_transactionRollsBack() {
        ScreenSharePushHandler pushHandler = mock(ScreenSharePushHandler.class);
        ScreenChangeBroadcaster broadcaster = new ScreenChangeBroadcaster(pushHandler);
        beginTransaction();

        broadcaster.publishAfterCommit(1L, TOKEN, "updated");
        // 回滚路径：synchronization 被清掉，afterCommit() 从来不会被调用
        List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(pending).hasSize(1);
        verify(pushHandler, never()).broadcast(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("没有事务（单条自动提交）- 当场就是已落库，直接推")
    void should_pushImmediately_when_noTransactionActive() {
        ScreenSharePushHandler pushHandler = mock(ScreenSharePushHandler.class);
        ScreenChangeBroadcaster broadcaster = new ScreenChangeBroadcaster(pushHandler);
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        broadcaster.publishAfterCommit(2L, TOKEN, "published");

        verify(pushHandler).broadcast(2L, TOKEN, "published");
    }

    @Test
    @DisplayName("未发布的屏没有令牌 - 没有可订阅的通道，什么都不做")
    void should_skip_when_shareTokenMissing() {
        ScreenSharePushHandler pushHandler = mock(ScreenSharePushHandler.class);
        ScreenChangeBroadcaster broadcaster = new ScreenChangeBroadcaster(pushHandler);

        broadcaster.publishAfterCommit(3L, null, "updated");
        broadcaster.publishAfterCommit(3L, "  ", "updated");

        verify(pushHandler, never()).broadcast(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("推送抛错不能把已提交的变更变成 500")
    void should_swallowPushFailure_when_handlerThrows() {
        ScreenSharePushHandler pushHandler = mock(ScreenSharePushHandler.class);
        when(pushHandler.broadcast(anyLong(), anyString(), anyString())).thenThrow(new IllegalStateException("boom"));
        ScreenChangeBroadcaster broadcaster = new ScreenChangeBroadcaster(pushHandler);

        assertThatCode(() -> broadcaster.publishAfterCommit(4L, TOKEN, "updated")).doesNotThrowAnyException();
    }
}
