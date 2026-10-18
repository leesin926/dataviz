package com.dataviz.screen.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把"这张大屏变了"推到分享页通道，并且只在事务提交之后推。
 * <p>为什么必须等提交：客户端收到帧就回去重拉配置。如果在 {@code @Transactional} 里直接广播，
 * 提交前回滚的话，在线访客会被刷成一份库里没有的配置，而且没有轮询兜底 —— 这个错误状态会一直留在那块屏上。
 * 挂 afterCommit 之后：回滚 ⇒ 一帧都不发（访客保持旧配置，与库一致）；提交 ⇒ 发一次。
 * "提交成功但推送失败"这段窗口不做补偿，由访客下一次重连补拉纠正。</p>
 * <p>{@code @Transactional} 之外的调用（比如单条自动提交的更新）当场发就是"已落库之后"，同样正确。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenChangeBroadcaster {

    private final ScreenSharePushHandler pushHandler;

    /**
     * @param shareToken 分享令牌；为空说明这张屏还没发布过，没有可订阅的通道，直接什么都不做
     */
    public void publishAfterCommit(Long screenId, String shareToken, String reason) {
        if (shareToken == null || shareToken.trim().isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doPublish(screenId, shareToken, reason);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(screenId, shareToken, reason);
            }
        });
    }

    private void doPublish(Long screenId, String shareToken, String reason) {
        try {
            pushHandler.broadcast(screenId, shareToken, reason);
        } catch (RuntimeException e) {
            // 推送失败不能把"改大屏"变成 500：配置已经落库，访客重连时会补拉
            log.error("大屏变更推送失败（不影响已提交的配置）: screenId={}, reason={}, err={}",
                    screenId, reason, e.getMessage(), e);
        }
    }
}
