package com.dataviz.alert.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleVO {

    private Long id;

    private String name;

    private String description;

    private String type;

    private Long datasourceId;

    private String metricExpression;

    private String condition;

    private BigDecimal threshold;

    private Integer duration;

    private String severity;

    private List<String> notifyChannels;

    /**
     * 这条规则挂的通知组（带每组的可达地址数）。
     * <p>
     * 一起回名字和地址数，是为了让规则列表能显示"发给了谁"、编辑弹窗能在<b>勾选当场</b>
     * 提示"这个组没有邮箱地址，而你把渠道选了 EMAIL"，而不是等告警真触发、通知记一条 FAILED 才发现。
     * 组被删掉过的历史引用不会出现在这里（查不到就不编造名字）。
     */
    private List<NotifyGroupOptionVO> notifyGroups;

    /** 挂了通知组但组里一个人都没有（或组都停用了）：界面要把这件事显式说出来的标记 */
    private Boolean notifyTargetsEmpty;

    private Boolean enabled;

    private Long tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
