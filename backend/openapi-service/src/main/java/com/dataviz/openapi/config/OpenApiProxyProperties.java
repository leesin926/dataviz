package com.dataviz.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口代理的可调用目标白名单（SSRF 判据，口径对齐 alert-service 的 {@code OutboundUrlGuard}）：
 * 外部请求体里的 {@code apiPath} 永远不能直接变成服务端要敲的 URL —— 只有在这里显式列出的
 * 服务代码 + 路径模式才允许转发，且只放行内部只读端点。
 * <p>
 * 之所以要显式 {@code base-url} 而不是复用网关的 {@code lb://}：本服务的转发是绕开网关直连
 * localhost 兄弟端口（见 gateway-service 的 application.yml 路由表），目标集合必须是一个封闭清单；
 * 一旦"服务代码"是调用方给的、地址从注册中心现推，就等于把内网任意主机开放给了外部输入。
 * <p>
 * 未列出的服务想放开就在这里加（或 Nacos 覆盖 {@code openapi.proxy.services.*}），
 * 加了路径但上游没有 {@code /internal/} 端点也没用 —— 直连端口要过 common-security 的 JWT 链，
 * 白名单条目应只填验证过存在内部端点的路径。
 */
@Data
@Component
@ConfigurationProperties(prefix = "openapi.proxy")
public class OpenApiProxyProperties {

    /** 服务代码（如 model、datasource）→ 可调用目标 */
    private Map<String, Target> services = new LinkedHashMap<>();

    /** 建连超时（毫秒）。代理路径在用户请求线程上同步等待，必须有界，不沿用共享 bean 的 15s 口径。 */
    private int connectTimeoutMs = 3000;

    /** 读超时（毫秒） */
    private int readTimeoutMs = 10000;

    /**
     * 可信反代跳数：0（默认）只信 TCP 对端；N>0 表示确实前置了 N 层可信代理、
     * 每层往 X-Forwarded-For 末尾追加自己看到的对端，取从右往左第 N 项（size - N）。
     * 与 {@code OpenApiAuthFilter}、网关 {@code gateway.rate-limit.trusted-proxy-hops} 同一口径，
     * 绝不信最左值 —— 那是客户端自写的，而它进 openapi_log.ip 当审计字段。
     */
    private int trustedProxyHops = 0;

    @Data
    public static class Target {

        /** 上游基地址（含端口，不含路径），如 http://localhost:17083 */
        private String baseUrl;

        /**
         * 上游真实路径是否剥掉 /api 前缀（对齐网关路由里的 StripPrefix）：
         * 网关对 user-service 配了 StripPrefix=1，其控制器映射在 /user/internal，
         * 直连 17082 时就必须用剥掉 /api 的路径；model/datasource 等未剥前缀的服务用原路径。
         */
        private boolean stripApiPrefix = false;

        /**
         * 可调用的<b>调用方口径</b>路径模式（Ant 风格，支持 {id} 占位），只放行只读类端点。
         * 空列表 = 该服务未暴露任何内部只读端点，代理直接拒绝并说明原因，不打一条注定 401 的请求。
         */
        private List<String> paths = new ArrayList<>();
    }
}
