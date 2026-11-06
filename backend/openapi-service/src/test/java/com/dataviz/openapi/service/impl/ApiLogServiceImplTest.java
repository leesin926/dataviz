package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.PageResult;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import com.dataviz.openapi.dto.OpenApiLogQueryDTO;
import com.dataviz.openapi.entity.OpenApiClient;
import com.dataviz.openapi.entity.OpenApiLog;
import com.dataviz.openapi.mapper.OpenApiClientMapper;
import com.dataviz.openapi.mapper.OpenApiLogMapper;
import com.dataviz.openapi.test.MybatisPlusTableInfoHelper;
import com.dataviz.openapi.vo.OpenApiLogVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用日志读路径的三条硬口径：
 * ① 只投影安全列 —— 返回体里既不能有 openapi_client.app_secret，也不能有 request_params 原文
 *    （代理把 params 原样转成请求体，datasource 内部查询里那就是 SQL）；
 * ② 租户靠父表 openapi_client 收口 —— 日志表没有 tenant_id 列（在拦截器黑名单里），
 *    所以可见性来自「当前租户有哪些客户端」，不是日志表自己带条件；
 * ③ 空结果与错误分得开 —— 真的没人调用过 ⇒ total=0 的空表；条件打错 ⇒ 点名报错，
 *    绝不把配置错误显示成「这个 key 没调用过」（API-106 那族「看不见错误就只能误诊」）。
 * 顺带钉住分页形状与排序稳定性。
 */
@ExtendWith(MockitoExtension.class)
class ApiLogServiceImplTest {

    private static final String SECRET_9 = "sk-secret-of-client-9-DO-NOT-LEAK";
    private static final String REQUEST_PARAMS_RAW = "{\"sql\":\"SELECT * FROM pwd_table\"}";

    @Mock
    private OpenApiLogMapper logMapper;

    @Mock
    private OpenApiClientMapper clientMapper;

    private ApiLogServiceImpl service;

    @BeforeEach
    void setUp() {
        MybatisPlusTableInfoHelper.initFor(OpenApiLog.class, OpenApiClient.class);
        service = new ApiLogServiceImpl(logMapper, clientMapper);
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).username("admin").tenantId("1").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ---------- 列白名单 / 不泄露 ----------

    @Test
    @DisplayName("查询列白名单：只取可展示列，request_params 与 app_secret 一次都不进 select")
    void should_projectSafeColumnsOnly_when_readingLogPage() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);

        service.pageLogs(new OpenApiLogQueryDTO());

        String logSelect = captureLastLogWrapper().getSqlSelect();
        assertTrue(logSelect.contains("client_id"), logSelect);
        assertTrue(logSelect.contains("api_path"), logSelect);
        assertTrue(logSelect.contains("execution_time"), logSelect);
        assertFalse(logSelect.contains("request_params"), logSelect);

        // 定位调用方时也只取两列：app_secret 是 openapi_client 的列，读日志根本不需要它
        ArgumentCaptor<Wrapper<OpenApiClient>> clientCaptor = captorForClient();
        verify(clientMapper).selectList(clientCaptor.capture());
        String clientSelect = clientCaptor.getValue().getSqlSelect();
        assertTrue(clientSelect.contains("app_key"), clientSelect);
        assertFalse(clientSelect.contains("app_secret"), clientSelect);
    }

    @Test
    @DisplayName("返回体里没有任何凭据痕迹：即使实体带回了 secret/请求参数，序列化后也找不到它们")
    void should_neverExposeSecret_when_rowsMappedToVo() {
        OpenApiClient leaked = client(9L, "ak-9");
        leaked.setAppSecret(SECRET_9);
        stubClients(leaked);

        OpenApiLog row = logRow(1L, 9L, "/api/model/internal/dataset/7", 200);
        row.setRequestParams(REQUEST_PARAMS_RAW);
        stubPageWith(1L, Collections.singletonList(row));

        PageResult<OpenApiLogVO> page = service.pageLogs(new OpenApiLogQueryDTO());

        String json = toJson(page);
        assertFalse(json.contains(SECRET_9), json);
        assertFalse(json.toLowerCase().contains("appsecret"), json);
        assertFalse(json.contains("pwd_table"), json);
        assertFalse(json.toLowerCase().contains("requestparam"), json);
        // 安全列如实带出（不是"读到了但映射丢了"）
        assertEquals("/api/model/internal/dataset/7", page.getRecords().get(0).getApiPath());
        assertEquals(Integer.valueOf(200), page.getRecords().get(0).getResponseCode());
    }

    // ---------- 租户与调用方 ----------

    @Test
    @DisplayName("日志按当前租户可见的客户端 id 圈定：client_id IN (...)，租户值本身不拼进日志 SQL")
    void should_scopeByParentClientIds_when_sessionHasTenant() {
        stubClients(client(9L, "ak-9"), client(10L, "ak-10"));
        stubPage(0L);

        service.pageLogs(new OpenApiLogQueryDTO());

        LambdaQueryWrapper<OpenApiLog> wrapper = captureLastLogWrapper();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("client_id IN"), sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(9L), wrapper.getParamNameValuePairs().toString());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(10L), wrapper.getParamNameValuePairs().toString());
        // 日志表没有 tenant_id 列：拼进去就是 Unknown column，整页 500
        assertFalse(sql.contains("tenant_id"), sql);
        assertFalse(sql.contains("deleted"), sql);
    }

    @Test
    @DisplayName("无会话（免登/内部调用）不拼租户条件：与拦截器「无会话不注入」同口径，也不用全库客户端 id 拼 IN")
    void should_skipClientScope_when_noTenantSession() {
        SecurityContextHolder.clear();
        stubPage(0L);

        service.pageLogs(new OpenApiLogQueryDTO());

        String sql = captureLastLogWrapper().getSqlSegment();
        assertFalse(sql.contains("client_id IN"), sql);
        verify(clientMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("按 appKey 过滤：条件同样经父表解析，命中后把日志收到该客户端")
    void should_filterByAppKey_when_appKeyGiven() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);
        OpenApiLogQueryDTO query = new OpenApiLogQueryDTO();
        query.setAppKey(" ak-9 ");

        service.pageLogs(query);

        ArgumentCaptor<Wrapper<OpenApiClient>> clientCaptor = captorForClient();
        verify(clientMapper).selectList(clientCaptor.capture());
        // 逼出 SQL 段后参数才落进 paramNameValuePairs（MP 懒求值）
        AbstractWrapper<OpenApiClient, ?, ?> clientWrapper =
                (AbstractWrapper<OpenApiClient, ?, ?>) clientCaptor.getValue();
        clientWrapper.getSqlSegment();
        assertTrue(clientWrapper.getParamNameValuePairs().containsValue("ak-9"),
                clientWrapper.getParamNameValuePairs().toString());
        assertTrue(clientWrapper.getSqlSegment().contains("app_key ="), clientWrapper.getSqlSegment());

        LambdaQueryWrapper<OpenApiLog> logWrapper = captureLastLogWrapper();
        logWrapper.getSqlSegment();
        assertTrue(logWrapper.getSqlSegment().contains("client_id IN"), logWrapper.getSqlSegment());
        assertTrue(logWrapper.getParamNameValuePairs().containsValue(9L),
                logWrapper.getParamNameValuePairs().toString());
    }

    @Test
    @DisplayName("未知的 clientId/appKey → 404 点名是哪个条件打错，绝不当成「该 key 没调用过」的空表")
    void should_rejectWithNamedReason_when_appFilterMatchesNothing() {
        when(clientMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<OpenApiClient>());
        OpenApiLogQueryDTO query = new OpenApiLogQueryDTO();
        query.setClientId(999L);

        BizException e = assertThrows(BizException.class, () -> service.pageLogs(query));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        assertTrue(e.getMessage().contains("clientId=999"), e.getMessage());
        assertTrue(e.getMessage().contains("不属于当前租户"), e.getMessage());
        verify(logMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("当前租户压根没有客户端 → 合法空表（total=0），不打日志表、也不报错")
    void should_returnEmptyPageWithoutError_when_tenantHasNoClient() {
        stubClients();

        PageResult<OpenApiLogVO> page = service.pageLogs(new OpenApiLogQueryDTO());

        assertEquals(0L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        assertEquals(0, page.getTotalPages());
        verify(logMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("租户里一个客户端都没有时不回落到「不过滤」：否则就是跨租户读日志")
    void should_notFallBackToUnscoped_when_clientListEmpty() {
        stubClients();

        service.pageLogs(new OpenApiLogQueryDTO());

        verify(logMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    // ---------- 其余过滤条件 ----------

    @Test
    @DisplayName("时间区间/状态码分类/精确码/路径关键字/服务代码各自落成对应列的条件")
    void should_applyEveryColumnBackedFilter_when_allConditionsGiven() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);

        OpenApiLogQueryDTO query = new OpenApiLogQueryDTO();
        query.setStartTime(LocalDateTime.of(2026, 11, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 11, 6, 0, 0));
        query.setResult("CLIENT_ERROR");
        query.setResponseCode(401);
        query.setApiPath("dataset");
        query.setServiceCode("model");

        service.pageLogs(query);

        LambdaQueryWrapper<OpenApiLog> wrapper = captureLastLogWrapper();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("create_time >="), sql);
        assertTrue(sql.contains("create_time <="), sql);
        assertTrue(sql.contains("response_code BETWEEN"), sql);
        assertTrue(sql.contains("response_code ="), sql);
        assertTrue(sql.contains("api_path LIKE"), sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("%dataset%"),
                wrapper.getParamNameValuePairs().toString());
        // 没有 service_code 列：它按 api_path 的 /api/<代码>/ 前缀匹配，不新增实体字段
        assertTrue(wrapper.getParamNameValuePairs().containsValue("/api/model/%"),
                wrapper.getParamNameValuePairs().toString());
    }

    @Test
    @DisplayName("result=SUCCESS 按 2xx 判定（与代理 is2xxSuccess 同口径），FAIL 覆盖非 2xx 与空值")
    void should_classifyResultByStatusCodeRange_when_resultGiven() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);

        OpenApiLogQueryDTO success = new OpenApiLogQueryDTO();
        success.setResult("success");
        service.pageLogs(success);
        LambdaQueryWrapper<OpenApiLog> successWrapper = captureLastLogWrapper();
        successWrapper.getSqlSegment();
        assertTrue(successWrapper.getSqlSegment().contains("response_code BETWEEN"), successWrapper.getSqlSegment());
        assertTrue(successWrapper.getParamNameValuePairs().containsValue(200),
                successWrapper.getParamNameValuePairs().toString());

        OpenApiLogQueryDTO fail = new OpenApiLogQueryDTO();
        fail.setResult("FAIL");
        service.pageLogs(fail);
        LambdaQueryWrapper<OpenApiLog> failWrapper = captureLastLogWrapper();
        String failSql = failWrapper.getSqlSegment();
        assertTrue(failSql.contains("response_code IS NULL"), failSql);
        assertTrue(failSql.contains("OR"), failSql);
    }

    @Test
    @DisplayName("非法 result / 倒置时间区间 / 带路径的 serviceCode 一律 400 并点名可接受取值")
    void should_rejectWith400_when_conditionIsNotColumnBackedOrImpossible() {
        OpenApiLogQueryDTO badResult = new OpenApiLogQueryDTO();
        badResult.setResult("MAYBE");
        BizException e1 = assertThrows(BizException.class, () -> service.pageLogs(badResult));
        assertEquals(ErrorCode.BAD_REQUEST, e1.getErrorCode());
        assertTrue(e1.getMessage().contains("SUCCESS"), e1.getMessage());

        OpenApiLogQueryDTO reversed = new OpenApiLogQueryDTO();
        reversed.setStartTime(LocalDateTime.of(2026, 11, 6, 0, 0));
        reversed.setEndTime(LocalDateTime.of(2026, 11, 1, 0, 0));
        BizException e2 = assertThrows(BizException.class, () -> service.pageLogs(reversed));
        assertTrue(e2.getMessage().contains("startTime 不能晚于 endTime"), e2.getMessage());

        OpenApiLogQueryDTO badCode = new OpenApiLogQueryDTO();
        badCode.setServiceCode("/api/model/internal");
        BizException e3 = assertThrows(BizException.class, () -> service.pageLogs(badCode));
        assertTrue(e3.getMessage().contains("serviceCode"), e3.getMessage());

        verify(logMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }

    // ---------- 分页形状与排序 ----------

    @Test
    @DisplayName("分页形状：页码页长透传给 MyBatis-Plus，totalPages 按 total/pageSize 算，行逐条映射")
    void should_returnPageShape_when_querySucceeds() {
        stubClients(client(9L, "ak-9"));
        OpenApiLog row = logRow(1L, 9L, "/api/datasource/internal/query", 500);
        stubPageWith(37L, Arrays.asList(row, row, row));

        OpenApiLogQueryDTO query = new OpenApiLogQueryDTO();
        query.setPageNum(3);
        query.setPageSize(20);
        PageResult<OpenApiLogVO> page = service.pageLogs(query);

        ArgumentCaptor<Page<OpenApiLog>> pageCaptor = captorForPage();
        verify(logMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertEquals(3L, pageCaptor.getValue().getCurrent());
        assertEquals(20L, pageCaptor.getValue().getSize());

        assertEquals(37L, page.getTotal());
        assertEquals(3, page.getPageNum());
        assertEquals(20, page.getPageSize());
        assertEquals(2, page.getTotalPages());
        assertEquals(3, page.getRecords().size());
        assertEquals(Long.valueOf(1L), page.getRecords().get(0).getId());
        assertEquals(Integer.valueOf(500), page.getRecords().get(0).getResponseCode());
    }

    @Test
    @DisplayName("排序固定 create_time DESC, id DESC：同秒行靠 id 兜底，翻页才不重不漏")
    void should_orderStably_when_pageQueried() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);

        service.pageLogs(new OpenApiLogQueryDTO());

        String sql = captureLastLogWrapper().getSqlSegment();
        assertEquals(sql.indexOf("ORDER BY"), sql.lastIndexOf("ORDER BY"), "只应有一段排序: " + sql);
        assertTrue(sql.contains("create_time DESC"), sql);
        assertTrue(sql.contains("id DESC"), sql);
    }

    @Test
    @DisplayName("query 为 null 不 NPE：按默认页码页长走（控制器绑不上参数时的兜底形状）")
    void should_useDefaultPaging_when_queryNull() {
        stubClients(client(9L, "ak-9"));
        stubPage(0L);

        PageResult<OpenApiLogVO> page = service.pageLogs(null);

        assertEquals(1, page.getPageNum());
        assertEquals(10, page.getPageSize());
        assertEquals(0L, page.getTotal());
    }

    // ---------- helpers ----------

    private void stubClients(OpenApiClient... clients) {
        when(clientMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(clients));
    }

    private void stubPage(long total) {
        Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
        page.setTotal(total);
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
    }

    private void stubPageWith(long total, List<OpenApiLog> records) {
        Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
        page.setTotal(total);
        page.setRecords(records);
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
    }

    private static OpenApiClient client(Long id, String appKey) {
        return OpenApiClient.builder().id(id).appKey(appKey).appName("客户端" + id).build();
    }

    private static OpenApiLog logRow(Long id, Long clientId, String apiPath, Integer responseCode) {
        return OpenApiLog.builder()
                .id(id).clientId(clientId).appName("经营看板嵌入应用").apiPath(apiPath).method("GET")
                .responseCode(responseCode).executionTime(42L).ip("127.0.0.1")
                .createTime(LocalDateTime.of(2026, 11, 6, 8, 0))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Wrapper<OpenApiClient>> captorForClient() {
        return (ArgumentCaptor<Wrapper<OpenApiClient>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Page<OpenApiLog>> captorForPage() {
        return (ArgumentCaptor<Page<OpenApiLog>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Page.class);
    }

    /**
     * 读日志用 wrapper 的列名/SQL 段做断言。
     * {@code selectPage(Page, Wrapper)} 的第二个形参在 MP 里声明为 {@code Wrapper<T>}，
     * 实现内部传的是 LambdaQueryWrapper（继承 AbstractWrapper，有 getSqlSegment/getParamNameValuePairs）；
     * 若哪天换成 QueryWrapper 传参，只需改这一个转换点。
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<OpenApiLog> captureLastLogWrapper() {
        ArgumentCaptor<Wrapper<OpenApiLog>> captor =
                (ArgumentCaptor<Wrapper<OpenApiLog>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper, org.mockito.Mockito.atLeastOnce())
                .selectPage(any(Page.class), captor.capture());
        List<Wrapper<OpenApiLog>> all = captor.getAllValues();
        return (LambdaQueryWrapper<OpenApiLog>) all.get(all.size() - 1);
    }

    private static String toJson(Object value) {
        try {
            return new ObjectMapper().findAndRegisterModules().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
