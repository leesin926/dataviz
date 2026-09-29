package com.dataviz.alert.vo;

import com.dataviz.alert.engine.notifier.ChannelField;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一种渠道的自述：类型码、能不能真发出去、要哪一类收件人、配置项清单。
 * <p>
 * 由后端<b>注册表</b>导出而不是写死在前端 —— 加一种渠道只需要加一个 {@code @Component}，
 * 配置页自己就长出对应的表单；这也是"前端选了一个后端没有实现的类型"这类缺陷（wechat/feishu 曾经就是这样）
 * 从根上不会再出现的原因：界面能选到的，恰好就是有实现的。
 */
@Data
@Builder
public class ChannelSchemaVO {

    private String type;

    /** 这条<b>通道</b>有没有接上实现（不是"这条配置对不对"）：SMS 缺服务商凭据 ⇒ false */
    private boolean deliverable;

    /**
     * 这条渠道要哪一类收件人：{@code email} / {@code mobile} / {@code none}。
     * 前端靠它决定两件事：测试发送弹窗要不要给临时收件人输入框、规则表单要不要提示"这组里没有邮箱地址"。
     */
    private String targetKind;

    private List<ChannelField> fields;
}
