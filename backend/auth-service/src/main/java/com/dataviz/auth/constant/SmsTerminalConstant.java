package com.dataviz.auth.constant;

/**
 * 短信验证码的<strong>终端标识</strong>规则。
 * <p>
 * 同一个手机号可能同时在设计端、管理端、桌面端、三个 uni 端登录。验证码若只按手机号存，
 * 两个端就会互相踩：A 端刚发过码，B 端被 60 秒重发闸门挡住；A 端把码用掉（一次性消费），
 * B 端再输同一个码就得到"验证码已过期"。所以发码与校验的键加了终端这一段。
 * </p>
 * <p>
 * 这个值<strong>由前端声明、后端按白名单校验</strong>，不能直接拼进 Redis 键。
 * 未校验的字符串等于把"键命名空间"交给调用方——想要几条闸门就有几条，配额限制也会跟着失效。
 * </p>
 */
public final class SmsTerminalConstant {

    /**
     * 允许的终端标识，与前端各应用的 {@code SMS_TERMINAL} 字面量一一对应：
     * {@code admin}（管理端）、{@code pc-web}（设计端）、{@code pc-desktop}（桌面端）、
     * {@code mobile-app}、{@code tablet-app}、{@code mini-program}（三个 uni 端）。
     * <p>
     * 带连字符的名字不能做 Java 枚举常量，所以这里用正则做白名单而不是枚举；
     * 新增一个端要同时改这条规则和前端那份字面量，两边漏一边就是发码直接失败。
     * </p>
     */
    public static final String PATTERN = "^(admin|pc-web|pc-desktop|mobile-app|tablet-app|mini-program)$";

    private SmsTerminalConstant() {
    }
}
