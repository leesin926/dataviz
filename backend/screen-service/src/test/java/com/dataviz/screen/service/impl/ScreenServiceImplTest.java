package com.dataviz.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.screen.dto.ScreenUpsertDTO;
import com.dataviz.screen.entity.Screen;
import com.dataviz.screen.mapper.ScreenMapper;
import com.dataviz.screen.vo.ScreenListVO;
import com.dataviz.screen.vo.ScreenVO;
import com.dataviz.screen.websocket.ScreenChangeBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScreenServiceImpl} 的单元测试：这是 demo 大屏背后的 JSON blob 存储层，
 * 钉死三块决策面 —— 变体按端合并、分享令牌与发布/下架状态机、列表过滤走 Wrapper SQL 片段而非信任 mapper。
 * 依赖纯 mock（mapper / broadcaster），ObjectMapper 用真实实例走完整 JSON 往返。
 */
class ScreenServiceImplTest {

    private ScreenMapper screenMapper;
    private ScreenChangeBroadcaster broadcaster;
    private ObjectMapper objectMapper;
    private ScreenServiceImpl service;

    @BeforeAll
    static void setUpClass() {
        // screen-service 没有自己的 MybatisPlusTableInfoHelper 副本；为避免"第五份拷贝"，
        // 这里就用同样的两行 API 把 Screen 的 lambda 列缓存初始化好（list/getByShareToken 用 lambda wrapper）。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Screen.class);
    }

    @BeforeEach
    void setUp() {
        screenMapper = mock(ScreenMapper.class);
        broadcaster = mock(ScreenChangeBroadcaster.class);
        objectMapper = new ObjectMapper();
        service = new ScreenServiceImpl(screenMapper, objectMapper, broadcaster);
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("create - 缺省状态 draft、画布 1920x1080、viewCount 归零、适配模式 scale")
    void should_applyCreateDefaults_when_minimalDto() {
        // Given
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("销售总览");
        stubInsertAssignId(100L);

        // When
        ScreenVO vo = service.create(dto);

        // Then
        Screen saved = capturedInsert();
        assertEquals(0, saved.getStatus());
        assertEquals(Long.valueOf(0L), saved.getViewCount());
        assertEquals("scale", saved.getAdaptMode());
        assertTrue(saved.getConfigJson().contains("\"width\":1920"), saved.getConfigJson());
        assertTrue(saved.getConfigJson().contains("\"height\":1080"), saved.getConfigJson());
        assertEquals(null, saved.getVariantsJson());
        assertEquals("draft", vo.getStatus());
    }

    @Test
    @DisplayName("create - 组件列表序列化落库，无 variants 时该列为 null")
    void should_persistComponentsAsJson_when_create() {
        // Given
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("n");
        dto.setComponents(Arrays.asList(chart("bar"), chart("line")));
        stubInsertAssignId(7L);

        // When
        service.create(dto);

        // Then
        Screen saved = capturedInsert();
        assertTrue(saved.getComponentsJson().contains("bar"), saved.getComponentsJson());
        assertTrue(saved.getComponentsJson().contains("line"), saved.getComponentsJson());
        assertEquals(null, saved.getVariantsJson());
    }

    @Test
    @DisplayName("create - 名称为空直接拒绝，不碰库")
    void should_throw_when_createNameBlank() {
        // When
        ScreenUpsertDTO blank = new ScreenUpsertDTO();
        blank.setName("   ");

        BizException ex = assertThrows(BizException.class, () -> service.create(blank));

        // Then
        assertTrue(ex.getMessage().contains("大屏名称不能为空"), ex.getMessage());
        verify(screenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create - 显式 width/height 覆盖默认画布尺寸")
    void should_keepProvidedSize_when_create() {
        // Given
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("n");
        dto.setWidth(3840);
        dto.setHeight(2160);
        stubInsertAssignId(1L);

        // When
        service.create(dto);

        // Then
        String cfg = capturedInsert().getConfigJson();
        assertTrue(cfg.contains("\"width\":3840"), cfg);
        assertTrue(cfg.contains("\"height\":2160"), cfg);
    }

    @Test
    @DisplayName("create - 适配模式归一：fixed-width / responsive / 未知落 scale")
    void should_normalizeAdaptMode_when_create() {
        // Given/When/Then：三种输入分别落规范值
        assertEquals("fixed-width", createAndCaptureAdaptMode("FixedWidth"));
        assertEquals("responsive", createAndCaptureAdaptMode("RESPONSIVE"));
        assertEquals("scale", createAndCaptureAdaptMode("weird-mode"));
    }

    // ---------------- update ----------------

    @Test
    @DisplayName("update - 按 id 改名并广播 updated（携带既有 shareToken）")
    void should_updateFieldsAndBroadcast_when_update() {
        // Given
        Screen stored = screen(1L, "旧名", "draft-ish");
        stored.setShareToken("tok-123");
        stored.setConfigJson("{\"width\":1920,\"height\":1080}");
        when(screenMapper.selectById(1L)).thenReturn(stored);

        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("新名");
        dto.setDescription("desc");

        // When
        service.update(1L, dto);

        // Then
        ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
        verify(screenMapper).updateById(captor.capture());
        assertEquals("新名", captor.getValue().getName());
        assertEquals("desc", captor.getValue().getDescription());
        verify(broadcaster).publishAfterCommit(1L, "tok-123", "updated");
    }

    @Test
    @DisplayName("update - 名称为空白时不改名（保留库里旧名）")
    void should_keepExistingName_when_updateNameBlank() {
        // Given
        when(screenMapper.selectById(1L)).thenReturn(screen(1L, "保留我", "draft"));
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("   ");

        // When
        service.update(1L, dto);

        // Then
        assertEquals("保留我", capturedUpdate().getName());
    }

    @Test
    @DisplayName("update - 只改描述时不重建 config_json（无 config/components/layers/width/height 入参）")
    void should_notTouchConfigJson_when_onlyDescriptionChanged() {
        // Given
        Screen stored = screen(1L, "n", "draft");
        stored.setConfigJson("{\"width\":1234,\"height\":567}");
        when(screenMapper.selectById(1L)).thenReturn(stored);
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setDescription("只改这个");

        // When
        service.update(1L, dto);

        // Then：config_json 原样保留，没有被 buildConfigJson 重写
        assertEquals("{\"width\":1234,\"height\":567}", capturedUpdate().getConfigJson());
    }

    @Test
    @DisplayName("update - 提供 components 时同时重建 config_json 并覆盖 components_json")
    void should_rebuildConfigAndComponents_when_updateWithComponents() {
        // Given
        Screen stored = screen(1L, "n", "draft");
        stored.setConfigJson("{\"width\":100,\"custom\":\"keep\"}");
        when(screenMapper.selectById(1L)).thenReturn(stored);
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setComponents(Collections.<Object>singletonList(chart("pie")));

        // When
        service.update(1L, dto);

        // Then：原 config 里的 custom 键被合并保留，width 未给则沿用 existing（这里没被默认覆盖掉旧 width）
        Screen saved = capturedUpdate();
        assertTrue(saved.getComponentsJson().contains("pie"), saved.getComponentsJson());
        assertTrue(saved.getConfigJson().contains("\"custom\":\"keep\""), saved.getConfigJson());
    }

    @Test
    @DisplayName("update - 服务层本身不做租户/归属校验：换到别的 tenant 的行照样更新（现状）")
    void should_notEnforceTenant_when_updateById() {
        // Given：库里这行属于另一租户
        Screen foreign = screen(1L, "别人的大屏", "draft");
        foreign.setTenantId(999L);
        when(screenMapper.selectById(1L)).thenReturn(foreign);
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("我改了");

        // When：本服务没有租户判据，直接放行（归属校验在控制器/安全层，不在这里）
        service.update(1L, dto);

        // Then
        assertEquals("我改了", capturedUpdate().getName());
        verify(screenMapper).updateById(any());
    }

    @Test
    @DisplayName("update - id 不存在抛 NOT_FOUND")
    void should_throwNotFound_when_updateUnknownId() {
        // Given
        when(screenMapper.selectById(404L)).thenReturn(null);

        // When
        BizException ex = assertThrows(BizException.class,
                () -> service.update(404L, new ScreenUpsertDTO()));

        // Then
        assertTrue(ex.getMessage().contains("Screen not found: 404"), ex.getMessage());
    }

    // ---------------- publish / unpublish ----------------

    @Test
    @DisplayName("publish - 首次发布无令牌时生成 32 位十六进制分享令牌")
    void should_generateTokenOnFirstPublish_when_noToken() {
        // Given
        Screen stored = screen(1L, "n", "draft");
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.publish(1L);

        // Then
        Screen saved = capturedUpdate();
        assertEquals(1, saved.getStatus());
        assertEquals(32, saved.getShareToken().length());
        assertFalse(saved.getShareToken().contains("-"), saved.getShareToken());
        verify(broadcaster).publishAfterCommit(eq(1L), any(String.class), eq("published"));
    }

    @Test
    @DisplayName("publish - 已有令牌时原样保留，不重新生成")
    void should_keepExistingToken_when_republish() {
        // Given
        Screen stored = screen(1L, "n", "draft");
        stored.setShareToken("stable-token-000");
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.publish(1L);

        // Then
        assertEquals("stable-token-000", capturedUpdate().getShareToken());
        verify(broadcaster).publishAfterCommit(1L, "stable-token-000", "published");
    }

    @Test
    @DisplayName("publish - 重复发布幂等：状态恒为 1、令牌稳定不变")
    void should_beIdempotent_when_doublePublish() {
        // Given：已发布且带令牌
        Screen stored = screen(1L, "n", "published");
        stored.setStatus(1);
        stored.setShareToken("same-token");
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.publish(1L);
        service.publish(1L);

        // Then：两次都是 status=1 + 同一令牌
        ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
        verify(screenMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        for (Screen s : captor.getAllValues()) {
            assertEquals(1, s.getStatus());
            assertEquals("same-token", s.getShareToken());
        }
    }

    @Test
    @DisplayName("unpublish - 从未发布过的屏也能下架：状态归 0，令牌缺失照样广播")
    void should_unpublish_when_neverPublished() {
        // Given
        Screen stored = screen(1L, "n", "draft");
        stored.setStatus(0);
        stored.setShareToken(null);
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.unpublish(1L);

        // Then
        assertEquals(0, capturedUpdate().getStatus());
        verify(broadcaster).publishAfterCommit(1L, null, "unpublished");
    }

    @Test
    @DisplayName("publish - id 不存在抛 NOT_FOUND 且不广播")
    void should_throwNotFound_when_publishUnknownId() {
        // Given
        when(screenMapper.selectById(404L)).thenReturn(null);

        // When
        assertThrows(BizException.class, () -> service.publish(404L));

        // Then
        verify(broadcaster, never()).publishAfterCommit(anyLong(), any(), any());
    }

    // ---------------- getById ----------------

    @Test
    @DisplayName("getById - pc 端只增计数并按顶层配置返回，不展平变体")
    void should_incrementAndReturn_when_getByIdPc() {
        // Given
        Screen stored = screen(5L, "n", "published");
        stored.setStatus(1);
        stored.setConfigJson("{\"width\":1920,\"height\":1080}");
        stored.setVariantsJson("{\"mobile\":{\"width\":750,\"height\":1334}}");
        when(screenMapper.selectById(5L)).thenReturn(stored);

        // When
        ScreenVO vo = service.getById(5L, "pc");

        // Then
        assertEquals(1920, vo.getWidth());
        assertEquals("published", vo.getStatus());
        verify(screenMapper).incrementViewCount(5L);
    }

    @Test
    @DisplayName("getById - mobile 端展平：该端尺寸/组件覆盖顶层，未配的端回退 pc")
    void should_applyVariant_when_getByIdMobile() {
        // Given
        Screen stored = screen(5L, "n", "published");
        stored.setConfigJson("{\"width\":1920,\"height\":1080}");
        Map<String, Object> mobile = new LinkedHashMap<String, Object>();
        mobile.put("width", 750);
        mobile.put("components", Collections.<Object>singletonList(chart("mbar")));
        Map<String, Object> variants = new LinkedHashMap<String, Object>();
        variants.put("mobile", mobile);
        stored.setVariantsJson(json(variants));
        stored.setComponentsJson(json(Collections.<Object>singletonList(chart("pcbar"))));
        when(screenMapper.selectById(5L)).thenReturn(stored);

        // When
        ScreenVO vo = service.getById(5L, "mobile");

        // Then
        assertEquals(750, vo.getWidth());
        assertEquals(1080, vo.getHeight()); // height 该端未配 ⇒ 回退顶层
        assertEquals("mbar", firstChartType(vo.getComponents()));
        assertEquals(null, vo.getVariants()); // 展平后不带变体表
    }

    // ---------------- getByShareToken ----------------

    @Test
    @DisplayName("getByShareToken - 有效令牌 + 已发布 ⇒ 正常返回并增计数")
    void should_returnVo_when_validPublishedToken() {
        // Given
        Screen stored = screen(9L, "n", "published");
        stored.setStatus(1);
        stored.setShareToken("abc");
        stored.setConfigJson("{\"width\":1920,\"height\":1080}");
        when(screenMapper.selectOne(any())).thenReturn(stored);

        // When
        ScreenVO vo = service.getByShareToken("abc", null);

        // Then
        assertEquals(9L, vo.getId());
        assertEquals("published", vo.getStatus());
        verify(screenMapper).incrementViewCount(9L);
    }

    @Test
    @DisplayName("getByShareToken - 未知令牌 ⇒ NOT_FOUND")
    void should_throwNotFound_when_unknownToken() {
        // Given
        when(screenMapper.selectOne(any())).thenReturn(null);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.getByShareToken("nope", null));

        // Then
        assertTrue(ex.getMessage().contains("大屏不存在或链接无效"), ex.getMessage());
    }

    @Test
    @DisplayName("getByShareToken - 软删行被 mapper 过滤 ⇒ 与未知令牌同样的 NOT_FOUND（服务层不复查 deleted 列）")
    void should_throwNotFound_when_softDeletedRow() {
        // Given：逻辑删除命中 ⇒ selectOne 拿不到行（返回 null）
        when(screenMapper.selectOne(any())).thenReturn(null);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.getByShareToken("deleted-tok", "pc"));

        // Then：服务代码只判 status，不看 deleted；对已删的兜底完全依赖 mapper 的 logic-delete
        assertTrue(ex.getMessage().contains("大屏不存在或链接无效"), ex.getMessage());
        verify(screenMapper, never()).incrementViewCount(any());
    }

    @Test
    @DisplayName("getByShareToken - 令牌为空串/空白直接拒绝")
    void should_throw_when_shareTokenBlank() {
        // When
        BizException ex = assertThrows(BizException.class, () -> service.getByShareToken("  ", null));

        // Then
        assertTrue(ex.getMessage().contains("分享标识不能为空"), ex.getMessage());
    }

    @Test
    @DisplayName("getByShareToken - 令牌命中但未发布 ⇒ 明确报未发布")
    void should_throw_when_unpublishedViaToken() {
        // Given
        Screen stored = screen(9L, "n", "draft");
        stored.setStatus(0);
        stored.setShareToken("abc");
        when(screenMapper.selectOne(any())).thenReturn(stored);

        // When
        BizException ex = assertThrows(BizException.class, () -> service.getByShareToken("abc", null));

        // Then
        assertTrue(ex.getMessage().contains("大屏尚未发布，分享链接不可用"), ex.getMessage());
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("delete - 走 deleteById（全局逻辑删除 ⇒ 软删）并按既有令牌广播 deleted")
    void should_softDeleteAndBroadcast_when_delete() {
        // Given
        Screen stored = screen(3L, "n", "published");
        stored.setShareToken("tok-del");
        when(screenMapper.selectById(3L)).thenReturn(stored);

        // When
        service.delete(3L);

        // Then：先查后删 —— 删之前拿到令牌才能广播给正在看的访客
        verify(screenMapper).deleteById(3L);
        verify(broadcaster).publishAfterCommit(3L, "tok-del", "deleted");
    }

    @Test
    @DisplayName("delete - id 不存在抛 NOT_FOUND 且不广播")
    void should_throwNotFound_when_deleteUnknownId() {
        // Given
        when(screenMapper.selectById(404L)).thenReturn(null);

        // When
        assertThrows(BizException.class, () -> service.delete(404L));

        // Then
        verify(screenMapper, never()).deleteById(any(Long.class));
    }

    // ---------------- list ----------------

    @Test
    @DisplayName("list - keyword/status 过滤写进捕获的 Wrapper SQL 片段（status 名转数字）")
    @SuppressWarnings("unchecked")
    void should_filterByKeywordAndStatus_when_list() {
        // Given
        when(screenMapper.selectList(any())).thenReturn(Collections.<Screen>emptyList());

        // When
        service.list("销售", "published", "pc", 1, 20);

        // Then
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(screenMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("name"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("order by") && sql.contains("desc"), sql); // 按创建时间倒序
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue("%销售%"), params.toString());
        assertTrue(params.containsValue(1), params.toString()); // published -> 1
    }

    @Test
    @DisplayName("list - 非法/未识别 status 不加过滤条件")
    @SuppressWarnings("unchecked")
    void should_skipStatusFilter_when_statusUnrecognized() {
        // Given
        when(screenMapper.selectList(any())).thenReturn(Collections.<Screen>emptyList());

        // When
        service.list(null, "bogus-status", null, 1, 20);

        // Then：parseStatus 认不出 ⇒ 返回 null ⇒ 不拼 status 条件
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(screenMapper).selectList(captor.capture());
        assertFalse(captor.getValue().getSqlSegment().toLowerCase().contains("status"),
                captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("list - 按端出尺寸：components 大字段清空但保留计数，variants 压成尺寸表")
    void should_slimRowsByPlatform_when_list() {
        // Given
        Screen stored = screen(1L, "n", "published");
        stored.setConfigJson("{\"width\":1920,\"height\":1080}");
        stored.setComponentsJson(json(Arrays.asList(chart("a"), chart("b"))));
        Map<String, Object> mobile = new LinkedHashMap<String, Object>();
        mobile.put("width", 750);
        mobile.put("height", 1334);
        mobile.put("components", Collections.<Object>singletonList(chart("only-for-mobile")));
        Map<String, Object> variants = new LinkedHashMap<String, Object>();
        variants.put("mobile", mobile);
        stored.setVariantsJson(json(variants));
        when(screenMapper.selectList(any())).thenReturn(Collections.singletonList(stored));

        // When
        ScreenListVO result = service.list(null, null, "mobile", 1, 20);

        // Then
        ScreenVO row = result.getList().get(0);
        assertEquals(750, row.getWidth());           // 该端单独配的宽高覆盖顶层
        assertEquals(1334, row.getHeight());
        assertEquals(null, row.getComponents());      // 大字段不带出
        assertEquals(Integer.valueOf(2), row.getComponentCount()); // 计数保留
        assertEquals(null, row.getVariants());
        assertEquals(750, sizeOf(row.getVariantSizes(), "mobile", "width"));
        assertEquals(1334, sizeOf(row.getVariantSizes(), "mobile", "height"));
    }

    @Test
    @DisplayName("list - 分页参数夹取：pageNum<1→1、pageSize<=0→20、pageSize>100→100")
    void should_clampPaging_when_listParamsOutOfRange() {
        // Given
        when(screenMapper.selectList(any())).thenReturn(Collections.<Screen>emptyList());

        // When
        ScreenListVO vo = service.list(null, null, null, -3, 5000);

        // Then
        assertEquals(1, vo.getPageNum());
        assertEquals(100, vo.getPageSize());
        assertEquals(0, vo.getTotal());
        assertEquals(0, vo.getPages());

        // When：pageSize<=0 落 20
        ScreenListVO small = service.list(null, null, null, 1, 0);
        assertEquals(20, small.getPageSize());
    }

    // ---------------- clone ----------------

    @Test
    @DisplayName("clone - 未给新名时补“副本”，重置为草稿且不复用源 id")
    void should_defaultCopyNameAndReset_when_cloneBlankName() {
        // Given
        Screen source = screen(1L, "源大屏", "published");
        source.setStatus(1);
        source.setShareToken("src-token");
        source.setViewCount(888L);
        source.setComponentsJson(json(Collections.<Object>singletonList(chart("keepme"))));
        when(screenMapper.selectById(1L)).thenReturn(source);
        stubInsertAssignIdForClone(2L);

        // When
        ScreenVO vo = service.clone(1L, "   ");

        // Then
        Screen copy = capturedInsertForClone();
        assertEquals("源大屏 副本", copy.getName());
        assertEquals(0, copy.getStatus());
        assertEquals(Long.valueOf(0L), copy.getViewCount());
        assertEquals(null, copy.getShareToken());           // 不复制源令牌
        assertTrue(copy.getComponentsJson().contains("keepme"), copy.getComponentsJson());
        assertNotEquals(Long.valueOf(1L), copy.getId());    // 新 id，不复用
        assertEquals(2L, vo.getId());
    }

    @Test
    @DisplayName("clone - 给了新名就用新名，且同名不做唯一性拦截（现状）")
    void should_useGivenName_when_cloneWithName() {
        // Given：源和已有的另一块屏同名
        Screen source = screen(1L, "大屏", "draft");
        when(screenMapper.selectById(1L)).thenReturn(source);
        stubInsertAssignIdForClone(2L);

        // When：故意取一个可能与别行重名的名字，服务层没有唯一性判据
        service.clone(1L, "大屏");

        // Then
        assertEquals("大屏", capturedInsertForClone().getName());
    }

    // ---------------- saveVariant ----------------

    @Test
    @DisplayName("saveVariant - 按端合并：写入 mobile 不动 pc/tablet 已有载荷")
    void should_mergePerTerminal_when_saveVariantMobile() {
        // Given：库里 pc 与 tablet 已各配一份，mobile 缺省
        Screen stored = screen(1L, "n", "published");
        stored.setVariantsJson(json(mapOf(
                "pc", mapOf("width", 1920, "components", Collections.singletonList("pc-c")),
                "tablet", mapOf("width", 1024, "components", Collections.singletonList("tab-c")))));
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.saveVariant(1L, "mobile", mapOf("width", 750, "components", Collections.singletonList("m-c")));

        // Then
        Map<String, Object> saved = readMap(capturedUpdate().getVariantsJson());
        assertEquals(750, num(saved.get("mobile"), "width"));
        assertEquals("pc-c", firstOf(saved.get("pc"), "components"));
        assertEquals(1920, num(saved.get("pc"), "width"));
        assertEquals("tab-c", firstOf(saved.get("tablet"), "components"));
        verify(broadcaster).publishAfterCommit(eq(1L), any(), eq("updated"));
    }

    @Test
    @DisplayName("saveVariant - 端类型大小写不敏感，归一为小写键")
    void should_lowerCaseKey_when_saveVariantUpperCase() {
        // Given
        Screen stored = screen(1L, "n", "published");
        stored.setVariantsJson(null);
        when(screenMapper.selectById(1L)).thenReturn(stored);

        // When
        service.saveVariant(1L, "MOBILE", mapOf("width", 500));

        // Then：键被归一为 mobile（pc 不在合法端集合里被当成合法？pc 合法），这里断言存的是 mobile
        assertTrue(capturedUpdate().getVariantsJson().contains("\"mobile\""),
                capturedUpdate().getVariantsJson());
    }

    @Test
    @DisplayName("saveVariant - 非法端类型（如 desktop）被拒绝，完全不碰库")
    void should_throwIllegalPlatform_when_saveVariantUnknownTerminal() {
        // When
        BizException ex = assertThrows(BizException.class,
                () -> service.saveVariant(1L, "desktop", mapOf("width", 1)));

        // Then：VALID_PLATFORMS 只有 pc/mobile/tablet，desktop 非法
        assertTrue(ex.getMessage().contains("非法的端类型: desktop"), ex.getMessage());
        verify(screenMapper, never()).updateById(any());
        verify(broadcaster, never()).publishAfterCommit(any(), any(), any());
    }

    @Test
    @DisplayName("saveVariant - id 不存在抛 NOT_FOUND")
    void should_throwNotFound_when_saveVariantUnknownId() {
        // Given
        when(screenMapper.selectById(404L)).thenReturn(null);

        // When
        BizException ex = assertThrows(BizException.class,
                () -> service.saveVariant(404L, "mobile", mapOf("width", 1)));

        // Then
        assertTrue(ex.getMessage().contains("Screen not found: 404"), ex.getMessage());
    }

    // ---------------- helpers ----------------

    private String createAndCaptureAdaptMode(String adaptMode) {
        ScreenUpsertDTO dto = new ScreenUpsertDTO();
        dto.setName("n");
        dto.setAdaptMode(adaptMode);
        // 每次重新 mock，避免相互污染
        ScreenMapper mapper = mock(ScreenMapper.class);
        ScreenChangeBroadcaster bc = mock(ScreenChangeBroadcaster.class);
        ScreenServiceImpl svc = new ScreenServiceImpl(mapper, objectMapper, bc);
        final Screen[] holder = new Screen[1];
        when(mapper.insert(any(Screen.class))).thenAnswer(inv -> {
            Screen s = inv.getArgument(0);
            s.setId(1L);
            holder[0] = s;
            return 1;
        });
        when(mapper.selectById(any())).thenAnswer(inv -> holder[0]);
        svc.create(dto);
        return holder[0].getAdaptMode();
    }

    // 保存 create 落库的那一行，供 selectOrThrow 之后 toVO 使用
    private final Screen[] inserted = new Screen[1];
    private Screen clonedInsert;

    private void stubInsertAssignId(final Long id) {
        when(screenMapper.insert(any(Screen.class))).thenAnswer(inv -> {
            Screen s = inv.getArgument(0);
            s.setId(id);
            inserted[0] = s;
            return 1;
        });
        when(screenMapper.selectById(any())).thenAnswer(inv -> inserted[0]);
    }

    private void stubInsertAssignIdForClone(final Long id) {
        when(screenMapper.insert(any(Screen.class))).thenAnswer(inv -> {
            Screen s = inv.getArgument(0);
            s.setId(id);
            clonedInsert = s;
            return 1;
        });
        // clone 末尾会 selectOrThrow(copy.getId())：返回克隆体本身
        when(screenMapper.selectById(id)).thenAnswer(inv -> clonedInsert);
    }

    private Screen capturedInsert() {
        ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
        verify(screenMapper).insert(captor.capture());
        return captor.getValue();
    }

    private Screen capturedInsertForClone() {
        ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
        verify(screenMapper).insert(captor.capture());
        return captor.getValue();
    }

    private Screen capturedUpdate() {
        ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
        verify(screenMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private Screen screen(Long id, String name, String statusName) {
        Screen s = new Screen();
        s.setId(id);
        s.setName(name);
        s.setStatus("published".equals(statusName) ? 1 : 0);
        s.setAdaptMode("scale");
        s.setViewCount(0L);
        return s;
    }

    private Map<String, Object> chart(String type) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("type", type);
        return m;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String jsonStr) {
        try {
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private String firstChartType(List<Object> components) {
        if (components == null || components.isEmpty()) {
            return null;
        }
        return String.valueOf(((Map<String, Object>) components.get(0)).get("type"));
    }

    @SuppressWarnings("unchecked")
    private Object num(Object terminalVariant, String key) {
        return ((Map<String, Object>) terminalVariant).get(key);
    }

    @SuppressWarnings("unchecked")
    private Object firstOf(Object terminalVariant, String key) {
        List<Object> list = (List<Object>) ((Map<String, Object>) terminalVariant).get(key);
        return list.isEmpty() ? null : list.get(0);
    }

    @SuppressWarnings("unchecked")
    private int sizeOf(Map<String, Object> variantSizes, String terminal, String key) {
        Map<String, Object> t = (Map<String, Object>) variantSizes.get(terminal);
        return ((Number) t.get(key)).intValue();
    }
}
