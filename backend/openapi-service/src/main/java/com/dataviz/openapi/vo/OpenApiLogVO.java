package com.dataviz.openapi.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「开放接口调用日志」的行视图：{@code openapi_log} 里<b>可以安全展示</b>的那几列。
 *
 * <p>两条不给的东西，都是硬口径不是遗漏：
 * <ul>
 *   <li><b>AppSecret / AppKey</b>：日志按 id 与名称冗余记调用方，凭据本身永不进日志视图
 *       （{@code openapi_client.app_secret} 有列，但这一面一次都不读它 ——
 *       查询走 {@code ApiLogServiceImpl#pageLogs}，那里连 select 列表都只取客户端 id 与 appKey 定位）；</li>
 *   <li><b>{@code request_params} 原文</b>：DDL 注释写的是「请求参数JSON」，而代理会把 params
 *       原样序列化转发（datasource 的内部查询里那就是 SQL、还可能夹带连接口令之类的字段值），
 *       头部同理不进表也不出表。所以这一面<b>没有详情端点</b>：列表已经把安全列全给了，
 *       单开一个 {@code /log/{id}} 只会多回一个 request_params。</li>
 * </ul>
 *
 * <p>失败原因（message）表里没有列，页面只能显示状态码与它的分类，见 {@link #responseCode} 的说明。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiLogVO {

    private Long id;

    /** 调用方客户端 id（{@code openapi_client.id}）；历史手工写入的行可能为 null */
    private Long clientId;

    /** 调用方名称，写入时的冗余快照：客户端改名后老行仍是旧名 */
    private String appName;

    /** 被调用的对外路径，形如 {@code /api/<服务代码>/...} */
    private String apiPath;

    /** HTTP 方法（代理只放行 GET/POST） */
    private String method;

    /**
     * 上游/代理返回的状态码，2xx 才算成功。
     * 表里<b>没有</b>失败原因列（{@code message}/{@code error} 都不存在），
     * 所以这里只有码：具体原因要么按状态码分类看，要么回 openapi-service 的日志查同一次调用。
     */
    private Integer responseCode;

    /** 本次调用耗时（毫秒），含白名单校验与转发等待 */
    private Long executionTime;

    /** 调用方地址，口径见 {@code OpenApiController#resolveClientIp}（默认只信 TCP 对端） */
    private String ip;

    private LocalDateTime createTime;
}
