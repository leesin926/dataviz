package com.dataviz.openapi.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.openapi.dto.OpenApiLogQueryDTO;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.vo.OpenApiLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「开放接口调用日志」的读端（pc-admin 开放平台 → 调用日志页）。
 *
 * <p><b>为什么另开一个控制器而不塞进 {@code OpenApiController}</b>：那个类映射在
 * {@code /api/openapi/proxy}、主体是外部应用（appKey/appSecret 在请求体里自证），
 * 它的类注释写死了「不上用户权限码」—— 给它加一个方法级 {@code @RequiresPermission} 的登录态读端点，
 * 等于在同一份 javadoc 里自相矛盾，也让「代理入口免权限」这条判据以后靠人肉记住。
 * 本服务的既有结构本来就是一个资源一个控制器（AppController / ApiKeyController / WebhookController），
 * 日志面按同样形状落在 {@code /api/openapi/log}，权限码与 AppController 读档一致。
 *
 * <p>免验签不受影响：{@code OpenApiAuthFilter#shouldNotFilter} 只拦 {@code /api/openapi/callback}，
 * 这一条走的是用户会话 + {@code openapi:read}。
 *
 * <p><b>只有分页一条读端点，没有 {@code /log/{id}} 详情</b>：{@code openapi_log} 里安全可展示的列
 * （时间、调用方、路径、方法、状态码、耗时、ip）已经在列表行里给全了，
 * 详情能多给的只有 {@code request_params} —— 那是代理原样转发的参数（datasource 的内部查询里就是 SQL，
 * 也可能夹带连接参数值），不属于「可安全展示」的列。所以这里不开详情，前端也不画详情按钮。
 * 同理：表里没有失败原因（message）列，页面只能显示状态码与分类。
 */
@Slf4j
@RestController
@RequestMapping("/api/openapi/log")
@RequiredArgsConstructor
@RequiresPermission("openapi:read")
public class OpenApiLogController {

    private final ApiLogService apiLogService;

    /**
     * 分页查调用日志。返回列白名单见 {@link OpenApiLogVO}：不含 appSecret、不含 request_params。
     * 空结果与错误是两件事 —— 真的没有调用时回 {@code total=0} 的空表，
     * 条件打错（客户端不存在/不在当前租户、result 取值非法、时间区间倒置）时报错并点名原因。
     */
    @GetMapping("/page")
    public R<PageResult<OpenApiLogVO>> page(OpenApiLogQueryDTO query) {
        return R.ok(apiLogService.pageLogs(query));
    }
}
