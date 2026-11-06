package com.dataviz.openapi.controller;

import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.annotation.RequiresPermission;
import com.dataviz.openapi.dto.OpenApiLogQueryDTO;
import com.dataviz.openapi.service.ApiLogService;
import com.dataviz.openapi.vo.OpenApiLogVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志读端的三件事，都用反射钉住，防止后面重构悄悄改掉：
 * ① 权限码在（口径与 {@code PermissionInterceptor} 完全一致：方法级优先、缺省回落类级），
 *    读档用的是既有码 {@code openapi:read}，不新造码；
 * ② URL 形状是 {@code GET /api/openapi/log/page} —— 前端 api-client 与 pc-admin 页面按这个字符串写死，
 *    路径改了必须两端一起改；
 * ③ 控制器只做信封（R.ok），空表与错误都原样交给下游，不在这里合成成功。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiLogControllerTest {

    @Mock
    private ApiLogService apiLogService;

    private OpenApiLogController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenApiLogController(apiLogService);
    }

    /** 与 PermissionInterceptor#preHandle 同一个解析顺序：方法注解优先，否则取类上的 merged 注解 */
    private static RequiresPermission effectivePermission(Method method) {
        RequiresPermission onMethod = method.getAnnotation(RequiresPermission.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequiresPermission.class);
    }

    @Test
    @DisplayName("handler 上有效权限码是 openapi:read：方法级或类级任一都要能解析出来，重构不许把它丢掉")
    void should_bePermissionGated_when_pageHandlerResolved() throws Exception {
        Method handler = OpenApiLogController.class.getMethod("page", OpenApiLogQueryDTO.class);

        RequiresPermission permission = effectivePermission(handler);

        assertTrue(permission != null, "openapi:read 权限注解不能整类删掉，读端点会变成任意登录用户可打的口子");
        assertArrayEquals(new String[]{"openapi:read"}, permission.value());
    }

    @Test
    @DisplayName("权限码不给写档：读日志不需要 openapi:write（那能铸凭据），两档粒度必须分开")
    void should_notRequireWriteCode_when_pageHandlerResolved() throws Exception {
        Method handler = OpenApiLogController.class.getMethod("page", OpenApiLogQueryDTO.class);

        RequiresPermission permission = effectivePermission(handler);

        assertTrue(permission != null);
        assertTrue(Arrays.stream(permission.value()).noneMatch(code -> code.endsWith(":write")),
                Arrays.toString(permission.value()));
    }

    @Test
    @DisplayName("URL 形状是 GET /api/openapi/log/page：前端按这个串写死，改动必须两端同步")
    void should_mapGetLogPage_when_urlContractChecked() throws Exception {
        Method handler = OpenApiLogController.class.getMethod("page", OpenApiLogQueryDTO.class);

        RequestMapping onClass = OpenApiLogController.class.getAnnotation(RequestMapping.class);
        GetMapping onMethod = handler.getAnnotation(GetMapping.class);

        assertTrue(onClass != null && onMethod != null, "读端点必须是 GET，且在 /api/openapi 前缀下");
        assertEquals("/api/openapi/log", onClass.value()[0]);
        assertEquals("/page", onMethod.value()[0]);
    }

    @Test
    @DisplayName("代理入口不受影响：/api/openapi/proxy 仍不套用户权限码（外部系统没有会话）")
    void should_keepProxyEndpointUngated_when_openApiControllerChecked() throws Exception {
        Method proxy = OpenApiController.class.getMethod("proxy",
                com.dataviz.openapi.dto.ApiProxyRequestDTO.class, javax.servlet.http.HttpServletRequest.class);

        assertTrue(effectivePermission(proxy) == null,
                "给代理入口加 openapi:* 等于要求外部系统先有平台账号会话，任何真实集成当场断");
    }

    @Test
    @DisplayName("控制器只套信封：服务返回的分页原样进 R.data，code 为 0")
    void should_wrapServicePageInEnvelope_when_pageCalled() {
        OpenApiLogVO row = OpenApiLogVO.builder().id(1L).clientId(9L).apiPath("/api/model/internal/dataset/7")
                .responseCode(200).executionTime(42L).build();
        PageResult<OpenApiLogVO> page =
                PageResult.of(Collections.singletonList(row), 1L, 1, 10);
        OpenApiLogQueryDTO query = new OpenApiLogQueryDTO();
        when(apiLogService.pageLogs(query)).thenReturn(page);

        R<PageResult<OpenApiLogVO>> response = controller.page(query);

        assertEquals(0, response.getCode());
        assertSame(page, response.getData());
        assertEquals(1L, response.getData().getTotal());
        assertEquals("/api/model/internal/dataset/7", response.getData().getRecords().get(0).getApiPath());
        verify(apiLogService).pageLogs(query);
    }
}
