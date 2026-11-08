package com.dataviz.alert.service;

import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.vo.AlertEventVO;
import com.dataviz.common.core.result.PageResult;

/**
 * {@code alert_event} 的<b>唯一</b> SQL 出口。
 *
 * <p>为什么要把读写都收进一个服务，而不是留在 {@code AlertEventController} /
 * {@code AlertCheckServiceImpl} / {@code AlertNotifyServiceImpl} 里各自摸 mapper：打开分片之后
 * {@code ShardInnerInterceptor} 拒绝一切<b>没绑分片键</b>的分片表语句，漏绑一处就是那条路径当场报错；
 * 而更坏的一种错是绑错了租户/时间 —— 行照样写成功，只是落进别的实例，事后按任何条件都捞不到
 * （PI4 的 API-166 就是这个形状）。把五个写点与四个读点收到一处，"忘了绑"就从五处变一处 review。
 */
public interface AlertEventService {

    /** 生成事件（分片开启时按这一行自己的 {@code tenantId} + {@code createTime} 路由）。 */
    AlertEvent create(AlertEvent event);

    /** 整行回写（acknowledge / resolve / notifiedAt 都走这里，路由键同样取自这一行本身）。 */
    void update(AlertEvent event);

    /**
     * 这条规则窗口内是否还有未处理（PENDING）事件 —— 重复告警抑制用。
     *
     * @param tenantId 路由键。调度线程没有会话，所以必须由调用方把规则的租户传进来，
     *                 不能在这里读 {@code SecurityContextHolder}
     */
    boolean hasOpenEvent(Long ruleId, Long tenantId);

    /** 按主键定位一条事件（分片开启时是<b>有界扫描</b>，裸 id 定不出月表）。 */
    AlertEvent locate(Long id);

    /** 状态流转：定位 → 改状态 → 回写。找不到就抛，不静默什么都不做。 */
    void markStatus(Long id, String status, boolean resolved);

    /** 管理端分页（分片开启时带时间窗与 truncated/shards 读数）。 */
    PageResult<AlertEventVO> page(Long ruleId, String status, String severity, int pageNum, int pageSize);

    /**
     * 状态计数。分片开启时这四个数都是<b>窗口内</b>的数，且窗口是服务端补的 —— 必须让调用方知道。
     * 租户取会话租户（这是管理端统计卡片的入口，路由键不能由调用方传，否则等于让人自选扫哪个实例）。
     */
    EventCounts counts();

    /** 事件侧四个计数；{@code partial} 为真时表示"这只是窗口/未截断范围内的数，不是全历史"。 */
    @lombok.Getter
    @lombok.Builder
    class EventCounts {
        private final long pending;
        private final long acknowledged;
        private final long resolved;
        private final long critical;
        private final boolean partial;
    }
}
