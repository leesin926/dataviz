package com.dataviz.alert.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertStatsVO {

    /**
     * Total number of alert rules
     */
    private Long totalRules;

    /**
     * Number of enabled rules
     */
    private Long enabledRules;

    /**
     * Number of pending events
     */
    private Long pendingEvents;

    /**
     * Number of acknowledged events
     */
    private Long acknowledgedEvents;

    /**
     * Number of resolved events
     */
    private Long resolvedEvents;

    /**
     * Total events count
     */
    private Long totalEvents;

    /**
     * Number of critical events
     */
    private Long criticalEvents;

    /**
     * 为真表示<b>这几个数不是全历史</b>：分片模式下它们只在服务端补的时间窗（最近 N 个月）里数过。
     * <p>
     * 没有这个标记时，页面会安心地把窗口内的数印成"共这么多告警"，而用户看不出它少了一段
     * —— 与 API-23 / API-168 同一族：查询收窄了范围就必须把这件事说出来，不能只体现在数字变小。
     * 单库（{@code shard.enabled=false}）时恒为 false。
     */
    private Boolean partial;
}
