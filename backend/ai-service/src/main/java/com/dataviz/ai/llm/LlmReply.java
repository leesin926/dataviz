package com.dataviz.ai.llm;

/**
 * 一次大模型调用的结果。
 *
 * <p>{@code available=false} 有两种完全不同的来源，必须分开说：
 * <b>没配置</b>（api-url/api-key 缺失或还是脚手架默认值）与<b>配了但调用失败</b>
 * （网络、超时、鉴权被拒、返回体不是预期形状）。前者是部署问题，后者是运行故障；
 * 把两者都写成"AI 暂时不可用"，值班的人就不知道该去填配置还是去看日志。</p>
 */
public class LlmReply {

    private final boolean available;
    private final String text;
    private final String reason;

    private LlmReply(boolean available, String text, String reason) {
        this.available = available;
        this.text = text;
        this.reason = reason;
    }

    public static LlmReply of(String text) {
        return new LlmReply(true, text, null);
    }

    public static LlmReply unavailable(String reason) {
        return new LlmReply(false, null, reason);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getText() {
        return text;
    }

    public String getReason() {
        return reason;
    }
}
