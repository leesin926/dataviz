package com.dataviz.alert.engine.notifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 这一次派发"<b>发给谁</b>"。
 * <p>
 * 它和渠道配置是两件事：渠道配置管的是<b>出口</b>（用哪个 SMTP、
 * 往哪个群机器人 POST），通知对象管的是<b>收件人</b>。原先收件人塞在渠道配置的 {@code to} /
 * {@code receivers} / {@code mobiles} 里，而派发侧每种渠道类型只取"启用中 id 最小"的那一条配置
 * （{@code NotifyDispatcher.findEnabledChannel}）⇒ <b>一条邮箱渠道只能带一组收件人，所有引用 EMAIL
 * 的规则共用它</b>，想按规则区分收件人就得再配一条渠道，而第二条同类型渠道永远不会被派发读到。
 * 这是收件人必须离开渠道配置的根本原因。
 * <p>
 * 传"已经按渠道分好类的地址列表"而不是把联系人对象丢给 notifier：notifier 不需要知道
 * 一个人有没有备注、属不属于某个组，它只需要"这封邮件发到哪几个地址"。
 */
public final class NotifyTargets {

    /** 收件人是从哪儿来的 —— 每种来源在界面上的说法都不一样，不能压成"有没有收件人"两态 */
    public enum Source {
        /** 规则挂的通知组解析出来的（正常路径） */
        GROUP,
        /** 规则没挂组，回退用渠道配置里<b>存量的</b> {@code to} / {@code receivers} / {@code mobile} ——
         * 老数据因此照旧发得出去，但界面要显式告诉用户这是历史遗留形状 */
        CHANNEL_CONFIG,
        /** 管理端"测试发送"弹窗里临时填的：它不属于任何规则，也不该被写进任何配置 */
        ADHOC,
        /** 两边都没有 */
        NONE
    }

    private static final NotifyTargets NONE = new NotifyTargets(Collections.<String>emptyList(),
            Collections.<String>emptyList(), Source.NONE);

    private final List<String> emails;
    private final List<String> mobiles;
    private final Source source;

    private NotifyTargets(List<String> emails, List<String> mobiles, Source source) {
        this.emails = emails;
        this.mobiles = mobiles;
        this.source = source;
    }

    public static NotifyTargets of(List<String> emails, List<String> mobiles, Source source) {
        return new NotifyTargets(distinct(emails), distinct(mobiles), source);
    }

    public static NotifyTargets none() {
        return NONE;
    }

    /** 邮件收件地址（已去重、去空） */
    public List<String> emails() {
        return emails;
    }

    /** 手机号：短信收件人，也是钉钉/企业微信机器人的 @ 对象 */
    public List<String> mobiles() {
        return mobiles;
    }

    public Source source() {
        return source;
    }

    public boolean hasEmails() {
        return !emails.isEmpty();
    }

    public boolean hasMobiles() {
        return !mobiles.isEmpty();
    }

    /** 日志与结果弹窗里展示用 */
    public String describe() {
        if (emails.isEmpty() && mobiles.isEmpty()) {
            return "";
        }
        List<String> all = new ArrayList<String>(emails);
        all.addAll(mobiles);
        return String.join(",", all);
    }

    private static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> kept = new LinkedHashSet<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                kept.add(value.trim());
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(kept));
    }
}
