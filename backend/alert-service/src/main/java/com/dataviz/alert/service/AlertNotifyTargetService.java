package com.dataviz.alert.service;

import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 通知对象（谁收件）与规则之间的解析、引用关系维护。
 * <p>
 * 单独一个服务而不是塞进 {@code AlertRuleService}：派发侧（{@code NotifyDispatcher}）、
 * 规则读写侧、通知组删除守卫三处都要问同一个问题"这个引用还在不在"，
 * 判据只写一份才不会三处各长歪。
 */
public interface AlertNotifyTargetService {

    /**
     * 解析这条规则在该渠道上实际要发给谁。
     * 规则挂的通知组解析不出任何地址时，<b>回退</b>到渠道配置里存量的收件人键
     * （{@code to} / {@code receivers} / {@code mobiles}），来源标记在返回值里，界面要照实说。
     */
    NotifyTargets resolveTargets(AlertRule rule, NotifyChannel channel);

    /** 这条规则挂了哪几个组（按组 id 升序，稳定输出便于比对） */
    List<Long> groupIdsOf(Long ruleId);

    /** 批量版：给规则列表页用，避免每条规则一次查询 */
    Map<Long, List<Long>> groupIdsOfRules(Collection<Long> ruleIds);

    /** 组 id → 组名（只回查得到的那些；被删掉的 id 不会出现在 map 里） */
    Map<Long, String> namesOfGroups(Collection<Long> groupIds);

    /**
     * 用 {@code groupIds} 整批替换这条规则挂的组。
     * null 表示"不改"（与 {@code notifyChannels} 的 null 语义一致）；空数组表示"清空"。
     * 入参里的组必须存在，否则 400 并点名哪几个 id 不存在 —— 不能静默丢掉，
     * 否则界面显示"挂了三个组"而库里只有两个。
     */
    void replaceRuleGroups(Long ruleId, List<Long> groupIds);

    /** 规则删除时一起清掉引用关系（这两张关联表没有逻辑删除列，是真删） */
    void clearRuleGroups(Long ruleId);
}
