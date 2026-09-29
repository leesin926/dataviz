package com.dataviz.alert.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 渠道配置页"测试发送"的结果。
 * <p>
 * 失败信息<b>当结果回</b>而不是抛异常：这类失败绝大多数是配置填错或地址不通，
 * 抛出去在界面上就是一条 500 红条，用户看不出是"我配错了"还是"服务坏了"。
 * 结果里的 {@code error} 就是 notifier 抛出的原话（含"通道未接入"那种解释）。
 */
@Data
@Builder
public class ChannelTestVO {

    private boolean success;

    /** 渠道类型，回显用 */
    private String channel;

    /** 脱敏后的接收方（token 已被掩掉），让操作的人确认"发到的是这个地址/这组收件人" */
    private String recipient;

    /**
     * 上面那串收件人是<b>从哪儿来的</b>：{@code ADHOC}（这次弹窗里临时填的）/ {@code CHANNEL_CONFIG}
     * （渠道里存的历史收件人）/ {@code NONE}（哪儿都没有，所以这条测试必然报"没有收件人"）。
     * 收件人已改到规则侧之后，配置页的测试<b>没有规则可解析</b>，不显式说来源的话，
     * 用户会以为"这里发的就是告警时会发的那批人"—— 而那批人其实由每条规则各自决定。
     */
    private String recipientSource;

    private String error;

    private long elapsedMs;

    /** 这条渠道是否具备真发送能力（当前只有短信为 false，此时 success 恒为 false） */
    private boolean deliverable;
}
