package com.dataviz.alert.engine.notifier;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * 服务端出网地址的准入判据。通知渠道的 URL 由管理端填写、由<b>服务端</b>照着发 POST，
 * 所以必须挡住"让服务端自己去敲内网/元数据段"的写法（SSRF）。
 * <p>
 * 判据放在<b>发送侧</b>而不是保存侧：渠道行可以直接由 SQL 写进 {@code notify_channel}，
 * 保存时校验拦不住那条路。配置页上线后<b>保存侧也判一次</b>（{@code NotifyChannelServiceImpl} 对
 * {@code url} 标记的字段调这里）—— 那一层是为了让填错的人当场看到红字，不是把本类从"必需"降级成"冗余"：
 * 绕过界面写进去的行依然只由发送侧这一道把关。两个配套说明：
 * <ul>
 *   <li>重定向：{@code notifyRestTemplate} 显式关掉了跟随 3xx。实测（报告 19.10 B 组）它原本就不跟随
 *       —— 但不是 JDK 的功劳，是 {@code SimpleClientHttpRequestFactory} 对非 GET 方法设了 {@code false}；
 *       所以那一步是防"换 factory / 开 {@code strictPostRedirect}"的加固，不是补一个当下可达的洞；</li>
 *   <li>残余风险：真正建连时底层还会再解析一次 DNS，域名型主机名存在重绑定（TOCTOU）窗口，
 *       本类不解决，只把字面量和常规解析结果挡住。</li>
 * </ul>
 * <p>
 * 刻意<b>不</b>屏蔽 10.x / 172.16-31.x / 192.168.x 私网段：本项目就是内网部署，内网自建
 * webhook 是正当用法，全挡等于砍功能。上公有云时要在 {@link #forbiddenReason} 里补厂商元数据地址
 * （阿里云 100.100.100.200 这类不在链路本地段里，本类当前挡不住）。
 */
public final class OutboundUrlGuard {

    private OutboundUrlGuard() {
    }

    /** 校验通过则原样返回 URL；不通过一律 BizException，消息里写清挡在哪一条 */
    public static String requireAllowed(String url, String channelName) {
        URI uri = parse(url, channelName);
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw reject(channelName, url, "URL 里没有主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw reject(channelName, url, "主机名解析不了: " + host);
        }
        // 一个域名可以解析出多条地址（含 IPv6），只要有一条落在禁段就整体拒掉
        for (InetAddress address : addresses) {
            String reason = forbiddenReason(address);
            if (reason != null) {
                throw reject(channelName, url, reason + " -> " + address.getHostAddress());
            }
        }
        return url;
    }

    private static URI parse(String url, String channelName) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw reject(channelName, url, "不是合法 URL");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw reject(channelName, url, "只支持 http/https");
        }
        return uri;
    }

    private static String forbiddenReason(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return "禁止回环地址（127.0.0.1/::1）";
        }
        if (address.isAnyLocalAddress()) {
            return "禁止 0.0.0.0 这类任意地址";
        }
        if (address.isLinkLocalAddress()) {
            return "禁止链路本地段（169.254.0.0/16、fe80::/10，云厂商元数据地址在这一段）";
        }
        if (address.isMulticastAddress()) {
            return "禁止组播地址";
        }
        return null;
    }

    private static BizException reject(String channelName, String url, String reason) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "通知渠道 " + channelName + " 的地址服务端不予访问：" + reason + "（url=" + url + "）");
    }
}
