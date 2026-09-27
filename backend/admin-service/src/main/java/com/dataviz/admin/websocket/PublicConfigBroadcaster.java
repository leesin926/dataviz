package com.dataviz.admin.websocket;

import com.dataviz.admin.config.PublicConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把"配置变了"这件事推给免登推送通道。
 * <p>
 * <b>为什么必须等事务提交</b>：改成推送之后就没有轮询兜底了 —— 客户端只在断开重连时补拉一次。
 * 如果在 {@code @Transactional} 方法里直接广播，一旦提交前回滚，在线客户端会被点亮成一个
 * 库里根本不存在的值，而且**永远不会自愈**。所以这里挂 afterCommit：
 * 回滚 ⇒ 一帧都不发（客户端保持旧值，与库一致）；提交 ⇒ 发一次。
 * 代价是"提交成功但推送失败"这一段窗口没有重试 —— 与轮询不同，这里的选择是不做补偿，
 * 漏掉的那次由客户端下一次重连补拉纠正（重连补拉是这条链唯一的自愈点，别去掉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicConfigBroadcaster {

    private final PublicConfigWebSocketHandler handler;

    /**
     * 非白名单键直接忽略（判据只在这里做一次，通道本身不认识业务）。
     *
     * @param value 写入后的新值
     */
    public void publishAfterCommit(String configKey, String value) {
        if (!PublicConfigKeys.isPublic(configKey)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务（单条 update 自动提交）：当场发就是"已落库之后"
            doPublish(configKey, value);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(configKey, value);
            }
        });
    }

    private void doPublish(String configKey, String value) {
        try {
            handler.broadcast(configKey, value);
        } catch (RuntimeException e) {
            // 推送失败不能把"改配置"这件事变成 500：值已经落库，客户端重连时会补拉
            log.error("免登配置推送失败（不影响已提交的配置）: key={}, err={}", configKey, e.getMessage(), e);
        }
    }
}
