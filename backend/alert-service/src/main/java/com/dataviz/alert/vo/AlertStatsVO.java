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
}
