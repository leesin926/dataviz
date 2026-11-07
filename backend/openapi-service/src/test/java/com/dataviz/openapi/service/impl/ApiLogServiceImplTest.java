package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.common.mybatis.shard.ShardMode;
import com.dataviz.common.mybatis.shard.ShardProperties;
import com.dataviz.common.mybatis.shard.ShardQueryFanout;
import com.dataviz.common.mybatis.shard.ShardRouteBy;
import com.dataviz.common.mybatis.shard.ShardRouter;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用日志读路径的三条硬口径：
 * ① 只投影安全列 —— 返回体里既不能有 openapi_client.app_secret，也不能有 request_params 原文
 *    （代理把 params 原样转成请求体，datasource 内部查询里那就是 SQL）；
 * ② 租户靠父表 openapi_client 收口 —— 日志的可见性来自「当前租户有哪些客户端」，不是日志表自己带条件：
 *    tenant_id 到 PI4 才补成可空派生列（鉴权失败的行天生没有它），且这张表在拦截器黑名单里，
 *    把租户拼在日志列上会连这些行一起筛掉；
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

    /** 分片关着时容器里没有 {@code ShardQueryFanout} ⇒ {@code getIfAvailable()} 给 null（下面 setUp 里 stub）。 */
    @Mock
    private org.springframework.beans.factory.ObjectProvider<ShardQueryFanout> shardFanoutProvider;

    private ApiLogServiceImpl service;

    @BeforeEach
    void setUp() {
        MybatisPlusTableInfoHelper.initFor(OpenApiLog.class, OpenApiClient.class);
        lenient().when(shardFanoutProvider.getIfAvailable()).thenReturn(null);
        service = new ApiLogServiceImpl(logMapper, clientMapper, shardFanoutProvider);
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).username("admin").tenantId("1").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    // ---------- 写侧：分片键与归属 ----------

    @Test
    @DisplayName("写侧自己把分片键说清楚：insert 期间线程上必须有 openapi_log 的绑定，值就是这一行的 create_time")
    void should_bindShardKeyDuringInsert_when_savingAuditRow() {
        List<String> seen = new ArrayList<String>();
        when(logMapper.insert(any(OpenApiLog.class))).thenAnswer(invocation -> {
            com.dataviz.common.mybatis.shard.ShardContext.Binding binding =
                    com.dataviz.common.mybatis.shard.ShardContext.current();
            seen.add(binding == null ? "UNBOUND"
                    : binding.getLogicalTable() + "/" + binding.getTimeValue());
            return 1;
        });

        service.saveLog(OpenApiLog.builder().clientId(9L).tenantId(903L)
                .createTime(LocalDateTime.of(2026, 11, 6, 10, 15, 30)).build());

        assertEquals(1, seen.size());
        assertTrue(seen.get(0).startsWith("openapi_log/"),
                "没绑分片键就插入：打开 shard.enabled 后这条要么被闸门拒绝，要么写进没人读的表 —— " + seen);
        assertTrue(seen.get(0).contains("2026-11-06T10:15:30"),
                "分片键必须是这一行自己的时间，不是当前时间（补记历史时两者会差出一个月）: " + seen);
        assertFalse(com.dataviz.common.mybatis.shard.ShardContext.isBound(),
                "作用域必须在 insert 之后退干净：线程池复用线程时残留绑定 = 下一个请求写进别人的分片");
    }

    @Test
    @DisplayName("create_time 没给时按当前时间绑，不拿 null 去当分片键")
    void should_fallBackToNow_when_createTimeMissing() {
        List<Object> values = new ArrayList<Object>();
        when(logMapper.insert(any(OpenApiLog.class))).thenAnswer(invocation -> {
            values.add(com.dataviz.common.mybatis.shard.ShardContext.current() == null ? null
                    : com.dataviz.common.mybatis.shard.ShardContext.current().getTimeValue());
            return 1;
        });

        service.saveLog(OpenApiLog.builder().clientId(9L).build());

        assertEquals(1, values.size());
        assertTrue(values.get(0) instanceof LocalDateTime,
                "缺 create_time 也要有一个可用的分片键，否则打开分片后这条写不进去: " + values);
    }

    @Test
    @DisplayName("归属只跟着验过签名的调用走：拒绝行把 tenant_id/client_id 留空，不替外部编租户")
    void should_keepIdentityColumnsNull_when_callerWasNotVerified() {
        List<OpenApiLog> written = new ArrayList<OpenApiLog>();
        when(logMapper.insert(any(OpenApiLog.class))).thenAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return 1;
        });

        service.saveLog(OpenApiLog.builder().apiPath("/api/openapi/callback/webhook/test").responseCode(401).build());

        assertEquals(1, written.size());
        assertNull(written.get(0).getTenantId(), "401 的调用没验过身份，凭空一个租户等于把别人的账记到这个租户头上");
        assertNull(written.get(0).getClientId());
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

    // ---------- 分片读路径（shard.enabled=true 时才会走到） ----------

    @Test
    @DisplayName("开着分片：跨三个月的区间要扫三个分片，归并后按全局新→旧出页")
    void should_fanOutAcrossMonthsWithFlags_when_timeRangeGiven() {
        stubClients(client(9L, "ak-9"));
        final AtomicInteger shard = new AtomicInteger();
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            int index = shard.getAndIncrement();
            // 每个分片一行，时间从新到旧（扇出按计划是 202611 → 202610 → 202609）
            Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
            page.setRecords(Collections.singletonList(
                    rowAt(30L - index * 10L, LocalDateTime.of(2026, 11 - index, 20, 8, 0))));
            return page;
        });
        when(logMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 2L, 3L);

        PageResult<OpenApiLogVO> result = serviceWithFanout(36).pageLogs(query(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 11, 30, 0, 0)));

        assertEquals(Boolean.FALSE, result.getTruncated());
        assertEquals(Integer.valueOf(3), result.getShards());
        // 区间是调用方给的 ⇒ 不许谎称"窗口是我补的"
        assertEquals(Boolean.FALSE, result.getWindowDefaulted());
        assertEquals(6L, result.getTotal());
        assertEquals(3, result.getRecords().size());
        assertEquals(Long.valueOf(30L), result.getRecords().get(0).getId());
        assertEquals(Long.valueOf(10L), result.getRecords().get(2).getId());
    }

    @Test
    @DisplayName("完全没给时间：默认收窄成最近 3 个月，并把 windowDefaulted=true 带出去")
    void should_defaultWindowAndFlagIt_when_neitherStartNorEndGiven() {
        stubClients(client(9L, "ak-9"));
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
            page.setRecords(Collections.emptyList());
            return page;
        });
        when(logMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        PageResult<OpenApiLogVO> result = serviceWithFanout(36).pageLogs(new OpenApiLogQueryDTO());

        assertEquals(Boolean.TRUE, result.getWindowDefaulted());
        // 3 个月的回看窗口**跨 4 张月表**：从今天往回推 3 个月落在 3 个多月前的月中，
        // 那个不完整的月份照样要扫。断言写成 3 会逼实现去做"月份对齐"，那是另一种语义（整月而不是滚动窗口）。
        assertEquals(Integer.valueOf(4), result.getShards());
    }

    @Test
    @DisplayName("扇出被分片上限截断时回 truncated=true，total 只是下界，不能当成一共调用了这么多次")
    void should_reportTruncation_when_shardCapCutTheScan() {
        stubClients(client(9L, "ak-9"));
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
            page.setRecords(Collections.singletonList(rowAt(1L, LocalDateTime.of(2026, 11, 20, 8, 0))));
            return page;
        });
        when(logMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        // 上限 2 个分片，区间却有 5 个月 ⇒ 少了 3 个月必须说出来
        PageResult<OpenApiLogVO> result = serviceWithFanout(2).pageLogs(query(
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 11, 30, 0, 0)));

        assertEquals(Boolean.TRUE, result.getTruncated());
        assertEquals(Integer.valueOf(2), result.getShards());
        assertEquals(10L, result.getTotal());
    }

    @Test
    @DisplayName("分片关着时行为与本轮之前逐字节相同：一次单表分页，三个分片字段全为 null")
    void should_keepSingleTablePathUntouched_when_shardingDisabled() {
        stubClients(client(9L, "ak-9"));
        stubPageWith(37L, Arrays.asList(logRow(1L, 9L, "/api/model/data", 200)));

        PageResult<OpenApiLogVO> result = service.pageLogs(query(
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 30, 0, 0)));

        assertNull(result.getTruncated());
        assertNull(result.getShards());
        assertNull(result.getWindowDefaulted());
        assertEquals(37L, result.getTotal());
        verify(logMapper).selectPage(any(Page.class), any(Wrapper.class));
        verify(logMapper, never()).selectCount(any(Wrapper.class));
    }

    /**
     * 扇出的计数 SQL 是我们自己发的第二条，分页插件不会替我们剥掉列投影。
     * 这一条单测钉的是 wrapper 的形状（投影 + 排序只能出现在行查询里），因为 mock 掉 mapper 之后
     * 「MP 把投影搬进 COUNT(...)」这件事要到真库跑一次才看得见 —— 本轮的运行时 500 就是这么来的。
     */
    @Test
    @DisplayName("扇出分两条 wrapper：行查询带列白名单与排序，计数只带 WHERE")
    void should_splitRowAndCountWrappers_when_fanOutCounting() {
        stubClients(client(9L, "ak-9"));
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
            page.setRecords(Collections.emptyList());
            return page;
        });
        when(logMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        serviceWithFanout(36).pageLogs(query(
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 30, 0, 0)));

        ArgumentCaptor<Wrapper<OpenApiLog>> rowCap = captorForLogWrapper();
        ArgumentCaptor<Wrapper<OpenApiLog>> countCap = captorForLogWrapper();
        verify(logMapper, atLeastOnce()).selectPage(any(Page.class), rowCap.capture());
        verify(logMapper, atLeastOnce()).selectCount(countCap.capture());

        LambdaQueryWrapper<OpenApiLog> row = (LambdaQueryWrapper<OpenApiLog>) rowCap.getValue();
        assertTrue(hasText(row.getSqlSelect()), "行查询必须带列白名单，否则 request_params 会跟着出来");
        assertFalse(row.getExpression().getOrderBy().isEmpty(), "行查询必须排序，否则翻页会重行/漏行");

        LambdaQueryWrapper<OpenApiLog> count = (LambdaQueryWrapper<OpenApiLog>) countCap.getValue();
        assertTrue(!hasText(count.getSqlSelect()),
                "计数带了列投影，MP 会拼成 COUNT(id, client_id, ...) —— MySQL 1064: " + count.getSqlSelect());
        assertTrue(count.getExpression().getOrderBy().isEmpty(),
                "计数带了 ORDER BY：COUNT(...) 后面跟排序在 only_full_group_by 下同样是死路");
        // 参数表要先把 SQL 渲出来才填得上（MP 3.5.5 的 in/ge 在渲染时才 formatParam），
        // 所以这里先取 sqlSegment 再断言参数，别指望 wrapper 构造完就有值。
        String countSql = count.getSqlSegment();
        assertTrue(countSql.contains("client_id IN") && countSql.contains("create_time >="),
                "计数不能是裸 COUNT(*)，租户与时间条件都得住着: " + countSql);
        assertTrue(count.getParamNameValuePairs().size() >= 3,
                "边界值必须是参数不能拼字符串: " + count.getParamNameValuePairs());
    }

    /**
     * 默认窗口不能只体现在"扫哪几张月表"上。最早那张月表里装着整月的行，
     * 而滚动窗口只覆盖它的后半段；不在 SQL 里画边界，total 就会把窗口外的调用也算进去，
     * 而调用方看到的是一个看起来完全正常的数字。
     */
    @Test
    @DisplayName("没给时间范围时，服务端补的窗口边界要进 SQL，不能只靠月表名收窄")
    void should_bindWindowBoundsIntoSql_when_windowIsDefaulted() {
        stubClients(client(9L, "ak-9"));
        when(logMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OpenApiLog> page = new Page<OpenApiLog>(1, 10);
            page.setRecords(Collections.emptyList());
            return page;
        });
        when(logMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        serviceWithFanout(36).pageLogs(new OpenApiLogQueryDTO());

        ArgumentCaptor<Wrapper<OpenApiLog>> cap = captorForLogWrapper();
        verify(logMapper, atLeastOnce()).selectCount(cap.capture());
        LambdaQueryWrapper<OpenApiLog> count = (LambdaQueryWrapper<OpenApiLog>) cap.getValue();
        String sql = count.getSqlSegment();
        assertTrue(sql.contains("create_time >=") && sql.contains("create_time <="),
                "补出来的窗口没进 SQL，最早那张月表整月都会被算进 total: " + sql);

        List<LocalDateTime> bounds = new ArrayList<LocalDateTime>();
        for (Object value : count.getParamNameValuePairs().values()) {
            if (value instanceof LocalDateTime) {
                bounds.add((LocalDateTime) value);
            }
        }
        assertEquals(2, bounds.size(), "窗口两个边界都得是参数，不能拼字符串: " + count.getParamNameValuePairs());
        Collections.sort(bounds);
        assertTrue(bounds.get(0).plusMonths(3).isEqual(bounds.get(1)),
                "默认回看 3 个月：下界+3 个月要正好等于上界，实际 " + bounds);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 用一份真的分片配置装出 service（实例 url 是假的：Hikari 懒初始化，这些测试不连库）。 */
    private ApiLogServiceImpl serviceWithFanout(int maxShardsPerRead) {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        properties.setMaxShardsPerRead(maxShardsPerRead);
        ShardProperties.Instance ds0 = new ShardProperties.Instance();
        ds0.setId("ds0");
        ds0.setUrl("jdbc:mysql://127.0.0.1:3306/db_openapi");
        ds0.setUsername("root");
        ds0.setPassword("unused-in-tests");
        ShardProperties.Instance ds1 = new ShardProperties.Instance();
        ds1.setId("ds1");
        ds1.setUrl("jdbc:mysql://127.0.0.1:3307/db_openapi");
        ds1.setUsername("root");
        ds1.setPassword("unused-in-tests");
        properties.setInstances(Arrays.asList(ds0, ds1));

        ShardProperties.Rule rule = new ShardProperties.Rule();
        rule.setTable("openapi_log");
        rule.setMode(ShardMode.MONTH);
        // 与生产同一条：这张表按月份轮转实例，租户只是审计列
        rule.setRouteBy(ShardRouteBy.NONE);
        rule.setBuckets(2);
        properties.setRules(Collections.singletonList(rule));
        properties.validate();

        ShardQueryFanout fanout = new ShardQueryFanout(new ShardRouter(properties));
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<ShardQueryFanout> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(fanout);
        return new ApiLogServiceImpl(logMapper, clientMapper, provider);
    }

    private static OpenApiLog rowAt(long id, LocalDateTime createTime) {
        return OpenApiLog.builder().id(id).clientId(9L).apiPath("/api/model/data").method("GET")
                .responseCode(200).createTime(createTime).build();
    }

    private static OpenApiLogQueryDTO query(LocalDateTime start, LocalDateTime end) {
        OpenApiLogQueryDTO dto = new OpenApiLogQueryDTO();
        dto.setStartTime(start);
        dto.setEndTime(end);
        return dto;
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
     * 日志 wrapper 的捕获器。{@code selectPage/selectCount} 的形参声明是 {@code Wrapper<T>}，
     * 实现里传的是 LambdaQueryWrapper（有 getSqlSelect / getExpression / getParamNameValuePairs），
     * 断言时在这一个点转型。
     */
    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Wrapper<OpenApiLog>> captorForLogWrapper() {
        return (ArgumentCaptor<Wrapper<OpenApiLog>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Wrapper.class);
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
