package com.dataviz.openapi.dto;

import com.dataviz.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 「开放接口调用日志」分页查询条件（{@code GET /api/openapi/log/page}）。
 *
 * <p><b>每个条件都必须有 {@code openapi_log} 的真实列撑着</b>（列清单来自
 * {@code deploy/sql/patch/2026-09-21-schema-completion.sql} —— 注意 init/*.sql 里 db_openapi 是零表，
 * 这四张表的 DDL 全在 patch 里；{@code tenant_id} 由 {@code deploy/sql/shard/2026-11-06-pi4-01-derive-tenant-id.sql}
 * 补成可空派生列）：
 * {@code client_id / app_name / api_path / method / request_params / response_code /
 * execution_time / ip / create_time}。刻意<b>没有</b>的条件：
 * <ul>
 *   <li>{@code tenantId}：列有，但它是 {@code client_id} 的派生物（审计用），租户一律由会话决定，
 *       不开放成入参 —— 让调用方指定租户就等于跨租户读别人的调用记录；
 *       {@code deleted} 则确实没有这一列（见 {@code ApiLogServiceImpl#pageLogs} 的口径说明）；</li>
 *   <li>{@code appId}（{@code openapi_app.id}）：日志的 {@code client_id} 指的是 {@code openapi_client.id}
 *       （DDL 注释与 {@code OpenApiController} 写入的都是它），两套凭据面的 id 空间不同，
 *       传应用 id 来查日志只会撞上同号的客户端、给出错的行；</li>
 *   <li>{@code appName}：能按 app_name 列过滤，但它只是写入时冗余的名称快照，
 *       改名后老行留旧名，按它筛会漏 —— 要精确定位就用 {@link #clientId} / {@link #appKey}。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenApiLogQueryDTO extends PageQuery {

    /**
     * 调用方客户端 id（{@code openapi_client.id}，不是 {@code openapi_app.id}）。
     * 与 {@link #appKey} 同时给出时按 AND 收口，两个条件都必须在当前租户内解析得通。
     */
    private Long clientId;

    /**
     * 调用方 AppKey。经 {@code openapi_client} 换成 id 再过滤日志（日志表没有 app_key 列），
     * 查不到就报 404 —— 不能悄悄当成「无过滤」，那会把「条件打错了」显示成「这个 key 没调用过」。
     */
    private String appKey;

    /**
     * 时间区间下界（含）。ISO 本地日期时间形状，如 {@code 2026-11-06T00:00:00}，
     * 与 monitor-service {@code /metric/history} 的入参口径一致（都不带时区，按服务端时区解释）。
     *
     * <p><b>这个注解不是装饰。</b>Spring Boot 不给裸 {@code LocalDateTime} 查询参数装 ISO 解析器
     * （默认按 locale 的 SHORT 样式），带 {@code T} 的值会在进入本方法之前就 400
     * {@code Failed to convert ... for property 'startTime'}——也就是"时间筛选"这个功能在页面上从来没通过。
     * 全局 {@code spring.mvc.format.date-time} 能治，但那要动 17 个服务的装配；这里只声明这两个字段自己要什么。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 时间区间上界（含）；口径同 {@link #startTime} */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    /**
     * 结果分类：{@code SUCCESS} / {@code FAIL} / {@code CLIENT_ERROR} / {@code SERVER_ERROR} / {@code OTHER}。
     * 取值外的字符串直接 400 并点名允许值，不静默忽略（口径同 collab-service 的状态码过滤）。
     */
    private String result;

    /** 精确上游状态码（如 401），与 {@link #result} 可同时给 */
    private Integer responseCode;

    /** 调用路径关键字，按 {@code api_path} 列做包含匹配 */
    private String apiPath;

    /**
     * 服务代码（如 {@code model}）。<b>表里没有 service_code 列</b>：它是 {@code api_path}
     * 的 {@code /api/<服务代码>/} 段，这里落成对该列的前缀匹配，不新增实体字段。
     */
    private String serviceCode;
}
