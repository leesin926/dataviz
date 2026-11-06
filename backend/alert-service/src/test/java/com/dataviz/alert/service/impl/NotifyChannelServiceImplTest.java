package com.dataviz.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.alert.dto.NotifyChannelDTO;
import com.dataviz.alert.engine.notifier.AlertNotifier;
import com.dataviz.alert.engine.notifier.ChannelField;
import com.dataviz.alert.engine.notifier.NotifyTargets;
import com.dataviz.alert.entity.AlertEvent;
import com.dataviz.alert.entity.AlertRule;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.AlertRuleMapper;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.dataviz.alert.service.AlertNotifyTargetService;
import com.dataviz.alert.test.MybatisPlusTableInfoHelper;
import com.dataviz.alert.vo.ChannelSchemaVO;
import com.dataviz.alert.vo.ChannelTestVO;
import com.dataviz.alert.vo.NotifyChannelVO;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.PageQuery;
import com.dataviz.common.core.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotifyChannelServiceImpl} 的单元测试。这一层握着渠道凭据（群机器人 token、SMTP 口令），
 * 所以钉死三件事：读接口永不回吐口令原文、保存侧按 schema 校验、引用完整性与"渠道存在但没实现"的遮蔽。
 * 依赖全是纯 mock（mapper / targetService），ObjectMapper 用真实实例以走完整 JSON 往返（与同目录既有测试一致）。
 */
class NotifyChannelServiceImplTest {

    private NotifyChannelMapper channelMapper;
    private AlertRuleMapper ruleMapper;
    private AlertNotifyTargetService targetService;
    private ObjectMapper objectMapper;

    private FakeNotifier email;
    private FakeNotifier webhook;

    /** 每次构造都注入这两个渠道：EMAIL=email 收件人形状，WEBHOOK=none 出口形状 */
    private NotifyChannelServiceImpl service;

    @BeforeAll
    static void setUpClass() {
        MybatisPlusTableInfoHelper.initFor(NotifyChannel.class, AlertRule.class);
    }

    @BeforeEach
    void setUp() {
        channelMapper = mock(NotifyChannelMapper.class);
        ruleMapper = mock(AlertRuleMapper.class);
        targetService = mock(AlertNotifyTargetService.class);
        objectMapper = new ObjectMapper();

        email = FakeNotifier.of("EMAIL", AlertNotifier.TARGET_EMAIL)
                .with(ChannelField.of("host", ChannelField.KIND_TEXT).required())
                .with(ChannelField.of("port", ChannelField.KIND_NUMBER))
                .with(ChannelField.of("password", ChannelField.KIND_TEXT).secret());
        webhook = FakeNotifier.of("WEBHOOK", AlertNotifier.TARGET_NONE)
                .with(ChannelField.of("url", ChannelField.KIND_TEXT).required().url())
                .with(ChannelField.of("headers", ChannelField.KIND_JSON));
        service = newService(Arrays.asList(email, webhook));
    }

    private NotifyChannelServiceImpl newService(List<AlertNotifier> notifiers) {
        return new NotifyChannelServiceImpl(channelMapper, ruleMapper, objectMapper, notifiers, targetService);
    }

    // ---------------- schemas ----------------

    @Test
    @DisplayName("schemas - 按类型码字典序导出注册表，字段照 notifier 声明原样带出")
    void should_exposeSchemasSorted_when_multipleNotifiersRegistered() {
        // Given：EMAIL、WEBHOOK 都注册了（TreeMap 按大写键排序）
        // When
        List<ChannelSchemaVO> schemas = service.schemas();

        // Then
        assertEquals(2, schemas.size());
        assertEquals("EMAIL", schemas.get(0).getType());
        assertEquals("WEBHOOK", schemas.get(1).getType());
        assertEquals(AlertNotifier.TARGET_EMAIL, schemas.get(0).getTargetKind());
        assertEquals(3, schemas.get(0).getFields().size());
        assertEquals("password", schemas.get(0).getFields().get(2).getKey());
        assertTrue(schemas.get(0).getFields().get(2).isSecret());
    }

    @Test
    @DisplayName("schemas - 同类型重复注册时后者覆盖前者（TreeMap 索引含义）")
    void should_keepLastNotifier_when_duplicateChannelRegistered() {
        // Given：两个 channel() 都是 EMAIL，第二个的 deliverable=false
        FakeNotifier emailDup = FakeNotifier.of("EMAIL", AlertNotifier.TARGET_EMAIL).with(
                ChannelField.of("host", ChannelField.KIND_TEXT).required());
        emailDup.deliverable = false;

        // When
        NotifyChannelServiceImpl dupService = newService(Arrays.asList(email, emailDup));
        List<ChannelSchemaVO> schemas = dupService.schemas();

        // Then：只剩一条 EMAIL，且保留的是后放进 map 的那个（deliverable=false、只声明一个字段）
        assertEquals(1, schemas.size());
        assertFalse(schemas.get(0).isDeliverable());
        assertEquals(1, schemas.get(0).getFields().size());
    }

    // ---------------- detail / page 的脱敏 ----------------

    @Test
    @DisplayName("detail - 口令字段只回掩码 ***，绝不回原文")
    void should_maskSecretValue_when_detail() {
        // Given
        NotifyChannel stored = channel(1L, "EMAIL",
                "{\"host\":\"mail.x.com\",\"port\":25,\"password\":\"s3cr3t-token\"}", true);
        when(channelMapper.selectById(1L)).thenReturn(stored);

        // When
        NotifyChannelVO vo = service.detail(1L);

        // Then
        assertEquals(ChannelField.MASK, vo.getConfig().get("password"));
        assertEquals("***", vo.getConfig().get("password"));
        assertNotContainsSecret(vo);
        assertEquals("mail.x.com", vo.getConfig().get("host"));
        assertTrue(vo.isDeliverable());
    }

    @Test
    @DisplayName("detail - 非口令字段与用户自写的多余键原样回显")
    void should_keepNonSecretAndExtraKeys_when_detail() {
        // Given：config 里有 schema 没声明的 note 键
        NotifyChannel stored = channel(2L, "EMAIL",
                "{\"host\":\"mail.x.com\",\"note\":\"留给值班\",\"password\":\"abc\"}", true);
        when(channelMapper.selectById(2L)).thenReturn(stored);

        // When
        Map<String, Object> cfg = service.detail(2L).getConfig();

        // Then
        assertEquals("留给值班", cfg.get("note"));
        assertEquals("mail.x.com", cfg.get("host"));
        assertEquals(ChannelField.MASK, cfg.get("password"));
    }

    @Test
    @DisplayName("detail - 渠道类型没有对应 notifier 时配置整条原样回吐（含口令）")
    void should_maskEveryNonEmptyValue_when_notifierMissingForType() {
        // Given：库里有一条 GHOST 类型（注册表里没有实现），config 里存着口令
        NotifyChannel stored = channel(3L, "GHOST", "{\"token\":\"raw-leak-secret\",\"webhook\":\"x\"}", true);
        when(channelMapper.selectById(3L)).thenReturn(stored);

        // When
        NotifyChannelVO vo = service.detail(3L);

        // Then：不知道哪个键算口令，就按最坏情况全部只回掩码（API-127 已修）。
        //       原先这段整段跳过脱敏，等于把明文交给任何持读权限的人。
        assertFalse("raw-leak-secret".equals(String.valueOf(vo.getConfig().get("token"))),
                "未注册 notifier 的类型仍把明文口令回给了调用方: " + vo.getConfig());
        assertFalse("x".equals(String.valueOf(vo.getConfig().get("webhook"))),
                String.valueOf(vo.getConfig()));
        assertEquals(2, vo.getConfig().size());
        assertFalse(vo.isDeliverable());
    }

    @Test
    @DisplayName("detail - 不存在的 id 抛 NOT_FOUND 并点名 id")
    void should_throwNotFound_when_detailUnknownId() {
        // Given
        when(channelMapper.selectById(99L)).thenReturn(null);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.detail(99L));

        // Then
        assertTrue(ex.getMessage().contains("通知渠道不存在: 99"));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("page - 每行口令同样脱敏，且带出 type/name 与分页口径")
    void should_maskSecretAndSetDeliverable_when_page() {
        // Given
        Page<NotifyChannel> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(
                channel(1L, "EMAIL", "{\"host\":\"h\",\"password\":\"topsecret\"}", true)));
        page.setTotal(1L);
        when(channelMapper.selectPage(any(), any())).thenReturn(page);
        when(channelMapper.selectList(any())).thenReturn(Collections.<NotifyChannel>emptyList());

        // When
        PageResult<NotifyChannelVO> result = service.page(pageQuery(1, 10), null, null);

        // Then
        assertEquals(1, result.getTotal());
        NotifyChannelVO row = result.getRecords().get(0);
        assertEquals(ChannelField.MASK, row.getConfig().get("password"));
        assertEquals("EMAIL", row.getType());
        assertNotContainsSecret(row);
    }

    @Test
    @DisplayName("page - keyword 走 name like、type 归一大写走 eq，写进捕获的 Wrapper SQL 片段")
    @SuppressWarnings("unchecked")
    void should_filterByNameAndUpperType_when_pageWithKeywordAndType() {
        // Given
        Page<NotifyChannel> page = new Page<>(1, 10);
        page.setRecords(Collections.<NotifyChannel>emptyList());
        page.setTotal(0L);
        when(channelMapper.selectPage(any(), any())).thenReturn(page);

        // When
        service.page(pageQuery(1, 10), "  运维  ", "webhook");

        // Then：从捕获的 wrapper 断言过滤条件，而不是信任 mapper
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(channelMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("name"), sql);
        assertTrue(sql.contains("type"), sql);
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        // keyword trim 后带百分号，type 归一为大写
        assertTrue(params.containsValue("%运维%"), params.toString());
        assertTrue(params.containsValue("WEBHOOK"), params.toString());
    }

    @Test
    @DisplayName("page - 非法分页参数被归一为第 1 页 / 每页 10 条")
    void should_defaultPaging_when_pageQueryValuesNonPositive() {
        // Given
        Page<NotifyChannel> page = new Page<>(1, 10);
        page.setRecords(Collections.<NotifyChannel>emptyList());
        page.setTotal(0L);
        when(channelMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<NotifyChannel> req = inv.getArgument(0);
            req.setRecords(Collections.<NotifyChannel>emptyList());
            req.setTotal(0L);
            return req;
        });

        // When
        PageResult<NotifyChannelVO> result = service.page(pageQuery(0, -5), null, null);

        // Then
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("create - 名称 trim、类型归一大写、enabled 缺省为 true")
    void should_trimNameAndDefaultEnabled_when_create() {
        // Given
        when(channelMapper.insert(any(NotifyChannel.class))).thenAnswer(inv -> {
            NotifyChannel c = inv.getArgument(0);
            c.setId(10L);
            return 1;
        });

        // When
        Long id = service.create(createDto(null, "  运维邮箱  ", "email", null,
                cfg("host", "smtp.x.com", "password", "pw")));

        // Then
        assertEquals(10L, id);
        ArgumentCaptor<NotifyChannel> captor = ArgumentCaptor.forClass(NotifyChannel.class);
        verify(channelMapper).insert(captor.capture());
        NotifyChannel saved = captor.getValue();
        assertEquals("运维邮箱", saved.getName());
        assertEquals("EMAIL", saved.getType());
        assertTrue(saved.getEnabled());
    }

    @Test
    @DisplayName("create - 口令原文确实落到持久化 config（读时才掩码）")
    void should_persistRealSecret_when_createWithSecret() {
        // Given
        when(channelMapper.insert(any(NotifyChannel.class))).thenAnswer(inv -> {
            NotifyChannel c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        // When
        service.create(createDto(null, "邮箱", "EMAIL", true, cfg("host", "h", "password", "real-pw")));

        // Then
        ArgumentCaptor<NotifyChannel> captor = ArgumentCaptor.forClass(NotifyChannel.class);
        verify(channelMapper).insert(captor.capture());
        assertTrue(captor.getValue().getConfig().contains("real-pw"), captor.getValue().getConfig());
    }

    @Test
    @DisplayName("create - 必填口令为空串时拒绝保存")
    void should_rejectMissingRequired_when_requiredSecretBlank() {
        // Given：password 是 secret 且此处标 required
        FakeNotifier required = FakeNotifier.of("EMAIL", AlertNotifier.TARGET_EMAIL)
                .with(ChannelField.of("password", ChannelField.KIND_TEXT).secret().required());
        NotifyChannelServiceImpl svc = newService(Collections.<AlertNotifier>singletonList(required));

        // When：提交空串（在 secret 语义里表示"清空"，但这里是必填 ⇒ 拒绝）
        BizException ex = assertThrows(BizException.class,
                () -> svc.create(createDto(null, "n", "EMAIL", true, cfg("password", ""))));

        // Then
        assertTrue(ex.getMessage().contains("EMAIL 渠道缺少必填配置: password"), ex.getMessage());
        verify(channelMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create - 数字型字段填非数字时拒绝")
    void should_rejectNonNumeric_when_numberFieldWrongType() {
        // When
        BizException ex = assertThrows(BizException.class, () -> service.create(
                createDto(null, "n", "EMAIL", true, cfg("host", "h", "port", "abc"))));

        // Then
        assertTrue(ex.getMessage().contains("port 必须是数字，当前是: abc"), ex.getMessage());
    }

    @Test
    @DisplayName("create - 枚举字段取值不在白名单时拒绝")
    void should_rejectValue_when_optionNotAllowed() {
        // Given：带 select 白名单的渠道
        FakeNotifier sms = FakeNotifier.of("SMS", AlertNotifier.TARGET_MOBILE)
                .with(ChannelField.of("sign", ChannelField.KIND_TEXT).options("阿里云", "腾讯云"));
        NotifyChannelServiceImpl svc = newService(Collections.<AlertNotifier>singletonList(sms));

        // When
        BizException ex = assertThrows(BizException.class,
                () -> svc.create(createDto(null, "n", "SMS", true, cfg("sign", "华为云"))));

        // Then
        assertTrue(ex.getMessage().contains("sign 只能取 阿里云、腾讯云，当前是: 华为云"), ex.getMessage());
    }

    @Test
    @DisplayName("create - url 字段填回环地址被 OutboundUrlGuard 当场拒绝")
    void should_rejectUrl_when_outboundUrlIsLoopback() {
        // When：127.0.0.1 是字面量，不需要 DNS 就能判为回环
        BizException ex = assertThrows(BizException.class, () -> service.create(
                createDto(null, "n", "WEBHOOK", true, cfg("url", "http://127.0.0.1/hook"))));

        // Then
        assertTrue(ex.getMessage().contains("服务端不予访问"), ex.getMessage());
        assertTrue(ex.getMessage().contains("禁止回环地址"), ex.getMessage());
    }

    @Test
    @DisplayName("create - url 字段填合法公网 IP 字面量时放行")
    void should_allowUrl_when_outboundUrlIsPublicLiteral() {
        // Given
        when(channelMapper.insert(any(NotifyChannel.class))).thenAnswer(inv -> {
            ((NotifyChannel) inv.getArgument(0)).setId(5L);
            return 1;
        });

        // When：8.8.8.8 是公网字面量，getAllByName 不查 DNS 即放行
        Long id = service.create(createDto(null, "n", "WEBHOOK", true, cfg("url", "http://8.8.8.8/hook")));

        // Then
        assertEquals(5L, id);
    }

    @Test
    @DisplayName("create - schema 未声明的多余键被保留而不是静默删除")
    void should_keepUnknownKeys_when_configHasExtraKeys() {
        // Given
        when(channelMapper.insert(any(NotifyChannel.class))).thenAnswer(inv -> {
            ((NotifyChannel) inv.getArgument(0)).setId(1L);
            return 1;
        });
        Map<String, Object> cfg = cfg("host", "h");
        cfg.put("customFlag", "keep-me");

        // When
        service.create(createDto(null, "n", "EMAIL", true, cfg));

        // Then
        ArgumentCaptor<NotifyChannel> captor = ArgumentCaptor.forClass(NotifyChannel.class);
        verify(channelMapper).insert(captor.capture());
        assertTrue(captor.getValue().getConfig().contains("keep-me"), captor.getValue().getConfig());
    }

    @Test
    @DisplayName("create - 名称超过 128 字被拒绝")
    void should_rejectTooLongName_when_create() {
        // Given
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            longName.append('a');
        }

        // When
        BizException ex = assertThrows(BizException.class, () -> service.create(
                createDto(null, longName.toString(), "EMAIL", true, cfg("host", "h"))));

        // Then
        assertTrue(ex.getMessage().contains("渠道名称超长"), ex.getMessage());
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("update - 缺 id 直接拒绝，不查库")
    void should_throwBadRequest_when_updateWithoutId() {
        // When
        BizException ex = assertThrows(BizException.class, () -> service.update(
                createDto(null, "n", "EMAIL", true, cfg("host", "h"))));

        // Then
        assertTrue(ex.getMessage().contains("更新通知渠道必须带 id"), ex.getMessage());
        verify(channelMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("update - 口令交回掩码 *** 时沿用库里那份，绝不被冲成 ***")
    void should_reuseStoredSecret_when_updateKeepsMask() {
        // Given：库里已存真口令
        NotifyChannel stored = channel(7L, "EMAIL", "{\"host\":\"h\",\"password\":\"keep-me-9\"}", true);
        when(channelMapper.selectById(7L)).thenReturn(stored);

        // When：前端把整个表单原样提交，口令位仍是掩码
        service.update(createDto(7L, "h2", "EMAIL", true, cfg("host", "h2", "password", ChannelField.MASK)));

        // Then：更新后的 config 里仍是原口令，而不是字面量 ***
        NotifyChannel saved = capturedUpdate();
        assertTrue(saved.getConfig().contains("keep-me-9"), saved.getConfig());
        assertFalse(saved.getConfig().contains("***"), saved.getConfig());
        assertEquals("h2", saved.getName());
    }

    @Test
    @DisplayName("update - 口令交回空串表示明确撤销该口令")
    void should_clearSecret_when_updateSendsEmptyString() {
        // Given
        NotifyChannel stored = channel(7L, "EMAIL", "{\"host\":\"h\",\"password\":\"old-secret\"}", true);
        when(channelMapper.selectById(7L)).thenReturn(stored);

        // When
        service.update(createDto(7L, "n", "EMAIL", true, cfg("host", "h", "password", "")));

        // Then：空串走 coerce→null，主字段循环不落 key；但"多余键保留"那趟循环会把 source 里的
        //       password="" 原样贴回 merged —— 旧口令被清空、但键以空字符串留在 config 里（疑似缺陷，见报告）。
        NotifyChannel saved = capturedUpdate();
        assertFalse(saved.getConfig().contains("old-secret"), saved.getConfig());
        Map<String, Object> savedCfg = readConfig(saved.getConfig());
        assertEquals("", savedCfg.get("password"), saved.getConfig());
        assertEquals("h", savedCfg.get("host"), saved.getConfig());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfig(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("update - 允许换类型，存储类型改为新 notifier 的大写码")
    void should_changeType_when_updateWithNewType() {
        // Given
        NotifyChannel stored = channel(7L, "EMAIL", "{\"host\":\"h\"}", true);
        when(channelMapper.selectById(7L)).thenReturn(stored);

        // When：换成 WEBHOOK（url 必填 ⇒ 提供合法公网字面量）
        service.update(createDto(7L, "n", "WEBHOOK", true, cfg("url", "http://8.8.8.8/x")));

        // Then
        assertEquals("WEBHOOK", capturedUpdate().getType());
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete - 仍有启用规则按该类型且无其它启用渠道时拒绝并点名规则")
    void should_rejectDelete_when_referencedAndNoOtherEnabled() {
        // Given
        when(channelMapper.selectById(3L)).thenReturn(channel(3L, "EMAIL", "{}", true));
        when(ruleMapper.selectList(any())).thenReturn(Collections.singletonList(rule("订单跌零")));
        when(channelMapper.selectCount(any())).thenReturn(0L);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.delete(3L));

        // Then
        assertTrue(ex.getMessage().contains("仍有启用中的告警规则按 EMAIL 渠道通知"), ex.getMessage());
        assertTrue(ex.getMessage().contains("订单跌零"), ex.getMessage());
        verify(channelMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("delete - 同类型还有其它启用渠道时放行")
    void should_allowDelete_when_anotherEnabledChannelExists() {
        // Given
        when(channelMapper.selectById(3L)).thenReturn(channel(3L, "EMAIL", "{}", true));
        when(ruleMapper.selectList(any())).thenReturn(Collections.singletonList(rule("r1")));
        when(channelMapper.selectCount(any())).thenReturn(1L);

        // When
        service.delete(3L);

        // Then
        verify(channelMapper).deleteById(3L);
    }

    @Test
    @DisplayName("delete - 没有任何规则引用该类型时放行，且不去数其它渠道")
    void should_allowDelete_when_noRuleReferencesType() {
        // Given
        when(channelMapper.selectById(3L)).thenReturn(channel(3L, "EMAIL", "{}", true));
        when(ruleMapper.selectList(any())).thenReturn(Collections.<AlertRule>emptyList());

        // When
        service.delete(3L);

        // Then
        verify(channelMapper).deleteById(3L);
        verify(channelMapper, never()).selectCount(any());
    }

    // ---------------- setEnabled ----------------

    @Test
    @DisplayName("setEnabled - 停用最后一条被规则引用的启用渠道时拒绝")
    void should_rejectSetEnabled_when_disablingLastReferencedEnabled() {
        // Given
        when(channelMapper.selectById(3L)).thenReturn(channel(3L, "EMAIL", "{}", true));
        when(ruleMapper.selectList(any())).thenReturn(Collections.singletonList(rule("r1")));
        when(channelMapper.selectCount(any())).thenReturn(0L);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.setEnabled(3L, false));

        // Then
        assertTrue(ex.getMessage().contains("停用后 EMAIL 渠道没有任何可用配置"), ex.getMessage());
        verify(channelMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("setEnabled - 只带 id/enabled 的补丁更新，不整条覆盖")
    void should_applyMinimalPatch_when_setEnabledTrue() {
        // Given
        when(channelMapper.selectById(3L)).thenReturn(channel(3L, "EMAIL", "{\"password\":\"p\"}", false));

        // When
        service.setEnabled(3L, true);

        // Then：补丁只填 id + enabled，config/name 留 null（updateById 会忽略 null 列）
        ArgumentCaptor<NotifyChannel> captor = ArgumentCaptor.forClass(NotifyChannel.class);
        verify(channelMapper).updateById(captor.capture());
        NotifyChannel patch = captor.getValue();
        assertEquals(3L, patch.getId());
        assertTrue(patch.getEnabled());
        assertEquals(null, patch.getConfig());
        assertEquals(null, patch.getName());
    }

    // ---------------- test ----------------

    @Test
    @DisplayName("test - 渠道存在但服务端没有对应实现时回失败并列出可用类型，不抛异常")
    void should_returnFailure_when_notifierMissingForType() {
        // Given
        when(channelMapper.selectById(4L)).thenReturn(channel(4L, "GHOST", "{}", true));

        // When
        ChannelTestVO vo = service.test(4L, null);

        // Then
        assertFalse(vo.isSuccess());
        assertFalse(vo.isDeliverable());
        assertTrue(vo.getError().contains("服务端没有 GHOST 渠道的实现"), vo.getError());
        assertTrue(vo.getError().contains("EMAIL"), vo.getError());
        assertEquals(0L, vo.getElapsedMs());
    }

    @Test
    @DisplayName("test - 临时收件人按 EMAIL 形状解析成 ADHOC 邮箱目标")
    void should_sendAdhocEmailTargets_when_testWithRecipients() {
        // Given
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "EMAIL", "{\"host\":\"h\"}", true));
        email.recipientValue = "ops@x.com";

        // When
        ChannelTestVO vo = service.test(1L, "a@x.com; b@x.com");

        // Then
        assertTrue(vo.isSuccess());
        assertEquals("ADHOC", vo.getRecipientSource());
        assertEquals("ops@x.com", vo.getRecipient());
        assertEquals("email:2", email.sentTargetsKind(NotifyTargets::emails));
        assertEquals(NotifyTargets.Source.ADHOC, email.sentTargets.source());
    }

    @Test
    @DisplayName("test - 无收件人概念的渠道即便填了临时收件人也报来源 NONE")
    void should_reportNoneSource_when_testWebhookWithRecipients() {
        // Given
        when(channelMapper.selectById(2L)).thenReturn(channel(2L, "WEBHOOK", "{\"url\":\"http://8.8.8.8/x\"}", true));
        webhook.recipientValue = "http://8.8.8.8/x";

        // When
        ChannelTestVO vo = service.test(2L, "someone@x.com");

        // Then
        assertTrue(vo.isSuccess());
        assertEquals("NONE", vo.getRecipientSource());
        assertEquals(NotifyTargets.Source.NONE, webhook.sentTargets.source());
    }

    @Test
    @DisplayName("test - 无临时收件人时走与派发同一套 targetService 解析")
    void should_useTargetService_when_testWithoutRecipients() {
        // Given
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "EMAIL", "{\"host\":\"h\"}", true));
        when(targetService.resolveTargets(any(), any())).thenReturn(
                NotifyTargets.of(Collections.singletonList("g@x.com"), Collections.<String>emptyList(),
                        NotifyTargets.Source.GROUP));
        email.recipientValue = "g@x.com";

        // When
        ChannelTestVO vo = service.test(1L, null);

        // Then
        assertEquals("GROUP", vo.getRecipientSource());
        verify(targetService).resolveTargets(any(AlertRule.class), any(NotifyChannel.class));
    }

    @Test
    @DisplayName("test - notifier 发送抛错时以 success=false + 原话回，绝不抛出 500")
    void should_returnFailure_when_notifierSendThrows() {
        // Given
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "EMAIL", "{\"host\":\"h\"}", true));
        email.sendError = new BizException(com.dataviz.common.core.result.ErrorCode.SERVICE_UNAVAILABLE,
                "SMTP 连接超时");

        // When
        ChannelTestVO vo = service.test(1L, "a@x.com");

        // Then
        assertFalse(vo.isSuccess());
        assertEquals("SMTP 连接超时", vo.getError());
    }

    @Test
    @DisplayName("test - 接收方解析抛错也不阻断，成功与否只看发送")
    void should_stillAttemptSend_when_recipientParsingThrows() {
        // Given
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "EMAIL", "{\"host\":\"h\"}", true));
        email.recipientThrows = true;

        // When
        ChannelTestVO vo = service.test(1L, "a@x.com");

        // Then：recipient 解析失败被吞掉，成功发送 ⇒ success=true，recipient 保持 null
        assertTrue(vo.isSuccess());
        assertEquals(null, vo.getRecipient());
    }

    // ---------------- 凭据绝不进日志 ----------------

    @Test
    @DisplayName("create - 调用方传入的口令原话绝不落进任何日志行")
    void should_neverLogSecret_when_createWithSecret() {
        // Given：把 NotifyChannelServiceImpl 的 logger 挂上内存 appender
        when(channelMapper.insert(any(NotifyChannel.class))).thenAnswer(inv -> {
            ((NotifyChannel) inv.getArgument(0)).setId(1L);
            return 1;
        });
        Logger target = (Logger) LoggerFactory.getLogger(NotifyChannelServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        ch.qos.logback.classic.Level before = target.getLevel();
        target.setLevel(ch.qos.logback.classic.Level.INFO);
        target.addAppender(appender);
        try {
            // When
            service.create(createDto(null, "邮箱", "EMAIL", true, cfg("host", "h", "password", "TOP-SECRET-9")));
        } finally {
            target.detachAppender(appender);
            target.setLevel(before);
        }

        // Then：日志里有 id/type/name，但绝不出现口令原话
        assertFalse(appender.list.isEmpty(), "应至少产生一条创建日志");
        for (ILoggingEvent event : appender.list) {
            String line = event.getFormattedMessage();
            assertFalse(line.contains("TOP-SECRET-9"), line);
        }
    }

    // ---------------- helpers ----------------

    private NotifyChannel capturedUpdate() {
        ArgumentCaptor<NotifyChannel> captor = ArgumentCaptor.forClass(NotifyChannel.class);
        verify(channelMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private void assertNotContainsSecret(NotifyChannelVO vo) {
        for (Map.Entry<String, Object> entry : vo.getConfig().entrySet()) {
            String v = String.valueOf(entry.getValue());
            assertFalse(v.contains("s3cr3t-token"), "VO 泄露口令: " + entry.getKey() + "=" + v);
            assertFalse(v.contains("topsecret"), "VO 泄露口令: " + entry.getKey() + "=" + v);
            assertFalse(v.contains("raw-leak-secret"), "VO 泄露口令: " + entry.getKey() + "=" + v);
        }
    }

    private PageQuery pageQuery(int num, int size) {
        PageQuery q = new PageQuery();
        q.setPageNum(num);
        q.setPageSize(size);
        return q;
    }

    private NotifyChannelDTO createDto(Long id, String name, String type, Boolean enabled, Map<String, Object> config) {
        NotifyChannelDTO dto = new NotifyChannelDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setType(type);
        dto.setEnabled(enabled);
        dto.setConfig(config);
        return dto;
    }

    private Map<String, Object> cfg(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private NotifyChannel channel(Long id, String type, String config, boolean enabled) {
        return NotifyChannel.builder().id(id).type(type).config(config).enabled(enabled)
                .name("ch-" + id).build();
    }

    private AlertRule rule(String name) {
        AlertRule r = new AlertRule();
        r.setName(name);
        r.setEnabled(true);
        return r;
    }

    /** 可控的渠道实现替身：声明字段、记录 send 入参、按需抛错 */
    private static final class FakeNotifier implements AlertNotifier {
        private String channel;
        private String targetKind;
        private boolean deliverable = true;
        private final List<ChannelField> fields = new ArrayList<ChannelField>();
        RuntimeException sendError;
        boolean recipientThrows;
        String recipientValue;
        NotifyChannel sentChannel;
        AlertRule sentRule;
        AlertEvent sentEvent;
        NotifyTargets sentTargets;

        static FakeNotifier of(String channel, String targetKind) {
            FakeNotifier n = new FakeNotifier();
            n.channel = channel;
            n.targetKind = targetKind;
            return n;
        }

        FakeNotifier with(ChannelField field) {
            fields.add(field);
            return this;
        }

        String sentTargetsKind(java.util.function.Function<NotifyTargets, List<String>> picker) {
            List<String> picked = picker.apply(sentTargets);
            return picked.isEmpty() ? null : targetKind + ":" + picked.size();
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public List<ChannelField> fields() {
            return fields;
        }

        @Override
        public boolean deliverable() {
            return deliverable;
        }

        @Override
        public String targetKind() {
            return targetKind;
        }

        @Override
        public String recipientOf(NotifyChannel channelConfig, NotifyTargets targets) {
            if (recipientThrows) {
                throw new IllegalStateException("recipient 解析炸了");
            }
            return recipientValue;
        }

        @Override
        public void send(NotifyChannel channelConfig, AlertRule rule, AlertEvent event, NotifyTargets targets) {
            this.sentChannel = channelConfig;
            this.sentRule = rule;
            this.sentEvent = event;
            this.sentTargets = targets;
            if (sendError != null) {
                throw sendError;
            }
        }
    }
}
