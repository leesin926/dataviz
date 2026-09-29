package com.dataviz.alert.dto;

import lombok.Data;

/**
 * 渠道配置页"测试发送"的入参。
 * <p>
 * {@code recipients} 是临时收件人，只用于这一次实测，<b>不落库</b> —— 收件人已经不属于渠道配置，
 * 但配置页需要一个"我不改规则也能确认这个出口通不通"的手段，所以留一个一次性的入口。
 * 留空则走与真实派发完全相同的解析路（按规则组/存量配置），这样"测试通过而实发发错人"不会回来。
 */
@Data
public class ChannelTestDTO {

    /** 逗号/分号/空白分隔；EMAIL 收邮箱地址，SMS/DINGTALK/WECHAT 收手机号，WEBHOOK/FEISHU 忽略 */
    private String recipients;
}
