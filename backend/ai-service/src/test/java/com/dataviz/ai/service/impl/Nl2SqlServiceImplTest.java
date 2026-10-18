package com.dataviz.ai.service.impl;

import com.dataviz.ai.dto.Nl2SqlDTO;
import com.dataviz.ai.llm.LlmClient;
import com.dataviz.ai.llm.LlmReply;
import com.dataviz.ai.schema.DatasourceSchemaReader;
import com.dataviz.ai.vo.Nl2SqlResultVO;
import com.dataviz.common.core.client.DatasourceQueryClient;
import com.dataviz.common.core.client.DatasourceQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NL2SQL 的口径：{@code llmUsed} 与 {@code llmNote} 是界面唯一能分辨"这次是真模型回答还是降级"的依据，
 * 所以三种状态（没模型 / 模型给了合规 SQL / 模型给了不合规 SQL）都要验到，
 * 尤其不能出现"没配置却返回一句看着能跑的 SQL"。
 */
@ExtendWith(MockitoExtension.class)
class Nl2SqlServiceImplTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private DatasourceQueryClient queryClient;

    private Nl2SqlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 用真实 DatasourceSchemaReader：SchemaResult 的构造器是包内的，
        // 拿真实读取器 + 桩数据源客户端，比 mock 掉整层更贴近线上走的那条路
        service = new Nl2SqlServiceImpl(llmClient, new DatasourceSchemaReader(queryClient));
    }

    @Test
    @DisplayName("未接入模型：退回注释而不是假 SQL，llmUsed=false 且原因里带上没拿到表结构")
    void should_returnCommentNotFakeSql_whenLlmUnavailable() {
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmReply.unavailable("未配置大模型服务（ai.llm.api-url / ai.llm.api-key）"));

        Nl2SqlResultVO vo = service.translate(dto("近 7 天销售额", null));

        assertFalse(vo.getLlmUsed());
        assertEquals(0.0, vo.getConfidence(), 0.0001);
        assertTrue(vo.getSql().startsWith("-- 未生成 SQL："), vo.getSql());
        assertTrue(vo.getSql().contains("近 7 天销售额"));
        assertTrue(vo.getLlmNote().contains("未配置大模型服务"), vo.getLlmNote());
        assertTrue(vo.getLlmNote().contains("未指定数据源"), vo.getLlmNote());
        verify(queryClient, never()).query(anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("模型给出合规 SELECT：剥掉围栏后放行，取到表结构时置信度 0.9")
    void should_acceptValidSql_whenSchemaPresent() {
        givenSchemaPresent();
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmReply.of(
                "```sql\nSELECT DATE(create_time) d, sum(amount) s FROM t_order GROUP BY d;\n```"));

        Nl2SqlResultVO vo = service.translate(dto("近 7 天销售额", 7L));

        assertTrue(vo.getLlmUsed());
        assertEquals("SELECT DATE(create_time) d, sum(amount) s FROM t_order GROUP BY d", vo.getSql());
        assertEquals(0.9, vo.getConfidence(), 0.0001);
        assertTrue(vo.getExplanation().contains("1 个列的真实表结构"), vo.getExplanation());
    }

    @Test
    @DisplayName("没取到表结构时仍能生成，但置信度降到 0.6 并说明是凭问题本身写的")
    void should_lowerConfidence_whenSchemaMissing() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmReply.of("SELECT 1"));

        Nl2SqlResultVO vo = service.translate(dto("随便看看", null));

        assertTrue(vo.getLlmUsed());
        assertEquals(0.6, vo.getConfidence(), 0.0001);
        assertTrue(vo.getExplanation().contains("未取到表结构"), vo.getExplanation());
        assertTrue(vo.getLlmNote().contains("未指定数据源"), vo.getLlmNote());
    }

    @Test
    @DisplayName("第一次不合规就给一次带原因的修正机会，修正后可用则用修正后的语句")
    void should_retryOnceWithReason_thenAcceptFixedSql() {
        givenSchemaPresent();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmReply.of("SELECT * FROM t_order; DELETE FROM t_order"))
                .thenReturn(LlmReply.of("SELECT id FROM t_order"));

        Nl2SqlResultVO vo = service.translate(dto("订单数", 7L));

        assertTrue(vo.getLlmUsed());
        assertEquals("SELECT id FROM t_order", vo.getSql());
        assertEquals(0.9, vo.getConfidence(), 0.0001);
        verify(llmClient, times(2)).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("修正后仍不合规：原样带回供人工判断，置信度压到 0.2 并明说不要直接执行")
    void should_returnRawWithLowConfidence_whenRetryStillInvalid() {
        givenSchemaPresent();
        String dirty = "DROP TABLE t_order";
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmReply.of(dirty))
                .thenReturn(LlmReply.of(dirty));

        Nl2SqlResultVO vo = service.translate(dto("删掉订单表", 7L));

        assertEquals(dirty, vo.getSql());
        assertTrue(vo.getLlmUsed(), "确实拿到过模型输出，这一条要标成走了模型");
        assertEquals(0.2, vo.getConfidence(), 0.0001);
        assertTrue(vo.getExplanation().contains("不要直接执行"), vo.getExplanation());
        assertTrue(vo.getLlmNote().contains("重试后仍不合规"), vo.getLlmNote());
    }

    @Test
    @DisplayName("不合规之后重试反而失败：仍按第一条不合规语句返回，同时带上重试失败原因")
    void should_keepFirstInvalidSql_whenRetryUnavailable() {
        givenSchemaPresent();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmReply.of("DELETE FROM t_order"))
                .thenReturn(LlmReply.unavailable("大模型服务返回 429"));

        Nl2SqlResultVO vo = service.translate(dto("清空订单", 7L));

        assertEquals("DELETE FROM t_order", vo.getSql());
        assertEquals(0.2, vo.getConfidence(), 0.0001);
        assertTrue(vo.getLlmNote().contains("429"), vo.getLlmNote());
    }

    @Test
    @DisplayName("解释空 SQL：不调模型，直接说明入参为空")
    void should_skipLlm_whenExplainSqlBlank() {
        Nl2SqlDTO dto = new Nl2SqlDTO();
        dto.setSql("  ");

        Nl2SqlResultVO vo = service.explain(dto);

        assertFalse(vo.getLlmUsed());
        assertEquals("入参 sql 为空", vo.getLlmNote());
        assertEquals("没有可解释的 SQL", vo.getExplanation());
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("解释的降级与成功两条路都要标对 llmUsed，并剥掉围栏")
    void should_explainBothPaths() {
        Nl2SqlDTO dto = new Nl2SqlDTO();
        dto.setSql("SELECT id FROM t_order");
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmReply.unavailable("无法访问大模型服务: timeout"));

        Nl2SqlResultVO degraded = service.explain(dto);
        assertFalse(degraded.getLlmUsed());
        assertEquals("SELECT id FROM t_order", degraded.getSql());
        assertTrue(degraded.getLlmNote().contains("timeout"), degraded.getLlmNote());

        Nl2SqlDTO fenced = new Nl2SqlDTO();
        fenced.setSql("```sql\nSELECT id FROM t_order;\n```");
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmReply.of("这条 SQL 取了订单表的主键列。"));

        Nl2SqlResultVO ok = service.explain(fenced);
        assertTrue(ok.getLlmUsed());
        assertEquals(0.85, ok.getConfidence(), 0.0001);
        assertEquals("SELECT id FROM t_order", ok.getSql());
        assertEquals("这条 SQL 取了订单表的主键列。", ok.getExplanation());
    }

    private void givenSchemaPresent() {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("table_name", "t_order");
        column.put("column_name", "id");
        column.put("data_type", "bigint");
        DatasourceQueryResult result = new DatasourceQueryResult();
        result.setRows(Collections.singletonList(column));
        when(queryClient.query(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(result);
    }

    private static Nl2SqlDTO dto(String question, Long datasourceId) {
        Nl2SqlDTO dto = new Nl2SqlDTO();
        dto.setQuestion(question);
        dto.setDatasourceId(datasourceId);
        return dto;
    }
}
