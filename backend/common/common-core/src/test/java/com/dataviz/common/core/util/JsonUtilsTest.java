package com.dataviz.common.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全站唯一 JSON 出入口。拦截器写 401/403 响应体、各服务解析内部接口回包都走这里，
 * 所以关键判据是"失败时到底抛还是吞"：吞成空对象会把数据丢失伪装成"没有数据"。
 * 这里把每条 fallback 的真实口径钉住。
 */
class JsonUtilsTest {

    public static class Sample {
        public Long id;
        public String name;
        public Inner inner;
        public LocalDateTime createdAt;

        public Sample() {
        }

        Sample(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Inner {
        public String value;

        public Inner() {
        }
    }

    @Test
    @DisplayName("toJson(null) 返回 Java null 而不是字符串 \"null\"")
    void should_returnNull_when_toJsonGivenNull() {
        // Given/When/Then
        assertNull(JsonUtils.toJson(null));
    }

    @Test
    @DisplayName("LinkedHashMap 按插入序序列化，输出可逐字节钉死")
    void should_writeExactJson_when_orderedMap() {
        // Given
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "操作成功");

        // When
        String json = JsonUtils.toJson(body);

        // Then
        assertEquals("{\"code\":0,\"message\":\"操作成功\"}", json);
    }

    @Test
    @DisplayName("LocalDateTime 输出 ISO 串（不是数组/时间戳）且能原样回读")
    void should_writeAndReadIso_when_localDateTime() {
        // Given
        Sample sample = new Sample(1L, "diana");
        sample.createdAt = LocalDateTime.of(2024, 5, 6, 7, 8, 9);

        // When
        String json = JsonUtils.toJson(sample);
        Sample back = JsonUtils.fromJson(json, Sample.class);

        // Then
        assertTrue(json.contains("\"createdAt\":\"2024-05-06T07:08:09\""), json);
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), back.createdAt);
    }

    @Test
    @DisplayName("带毫秒的 LocalDateTime 序列化保留小数秒，回读不丢精度")
    void should_keepNanos_when_localDateTimeHasFraction() {
        // Given
        Sample sample = new Sample();
        sample.createdAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123000000);

        // When
        String json = JsonUtils.toJson(sample);

        // Then
        assertTrue(json.contains("\"createdAt\":\"2024-01-02T03:04:05.123\""), json);
    }

    @Test
    @DisplayName("无法序列化的对象抛 RuntimeException，不返回半截 JSON")
    void should_throwRuntime_when_objectNotSerialisable() {
        // Given
        Object bean = new Object();

        // When
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonUtils.toJson(bean));

        // Then
        assertEquals("JSON序列化失败", ex.getMessage());
        assertTrue(ex.getCause() instanceof JsonProcessingException, String.valueOf(ex.getCause()));
    }

    @Test
    @DisplayName("fromJson(Class)：null 与纯空白都返回 null，不进解析器")
    void should_returnNull_when_jsonBlankOrNull() {
        // Given/When/Then
        assertNull(JsonUtils.fromJson(null, Sample.class));
        assertNull(JsonUtils.fromJson("", Sample.class));
        assertNull(JsonUtils.fromJson("   ", Sample.class));
    }

    @Test
    @DisplayName("fromJson 正常回读字段值")
    void should_bindFields_when_jsonValid() {
        // Given
        String json = "{\"id\":7,\"name\":\"alice\"}";

        // When
        Sample sample = JsonUtils.fromJson(json, Sample.class);

        // Then
        assertEquals(Long.valueOf(7), sample.id);
        assertEquals("alice", sample.name);
    }

    @Test
    @DisplayName("字面量 \"null\" 解析成 null：调用方不能把它当成空对象")
    void should_returnNull_when_jsonIsNullLiteral() {
        // Given/When/Then
        assertNull(JsonUtils.fromJson("null", Sample.class));
    }

    @Test
    @DisplayName("未知属性被忽略（FAIL_ON_UNKNOWN_PROPERTIES 关闭），已知字段照常绑定")
    void should_ignoreUnknownProperties_when_extraKeysPresent() {
        // Given
        String json = "{\"id\":1,\"name\":\"bob\",\"neverHeardOf\":\"x\",\"nested\":{\"a\":1}}";

        // When
        Sample sample = JsonUtils.fromJson(json, Sample.class);

        // Then
        assertEquals(Long.valueOf(1), sample.id);
        assertEquals("bob", sample.name);
    }

    @Test
    @DisplayName("坏 JSON 抛 RuntimeException(\"JSON反序列化失败\")，不静默返回 null/空对象")
    void should_throwRuntime_when_jsonMalformed() {
        // Given/When
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonUtils.fromJson("{oops", Sample.class));

        // Then
        assertEquals("JSON反序列化失败", ex.getMessage());
        assertTrue(ex.getCause() instanceof JsonProcessingException, String.valueOf(ex.getCause()));
    }

    @Test
    @DisplayName("ACCEPT_EMPTY_STRING_AS_NULL_OBJECT 生效：嵌套对象的空串入参落为 null")
    void should_bindNullObject_when_nestedValueIsEmptyString() {
        // Given
        String json = "{\"id\":1,\"inner\":\"\"}";

        // When
        Sample sample = JsonUtils.fromJson(json, Sample.class);

        // Then
        assertNull(sample.inner);
        assertEquals(Long.valueOf(1), sample.id);
    }

    @Test
    @DisplayName("TypeReference 版支持泛型集合，空白输入返回 null（不是空集合）")
    void should_bindGenericCollection_when_typeReferenceGiven() {
        // Given
        String json = "[{\"id\":1},{\"id\":2}]";

        // When
        List<Sample> list = JsonUtils.fromJson(json, new TypeReference<List<Sample>>() { });

        // Then
        assertEquals(2, list.size());
        assertEquals(Long.valueOf(2), list.get(1).id);
        assertNull(JsonUtils.fromJson(" ", new TypeReference<List<Sample>>() { }));
    }

    @Test
    @DisplayName("fromJsonList 元素类型由入参决定，数字数组可读成 Integer")
    void should_readElementList_when_listJsonGiven() {
        // Given/When
        List<Integer> ids = JsonUtils.fromJsonList("[1,2,3]", Integer.class);

        // Then
        assertEquals(Arrays.asList(1, 2, 3), ids);
    }

    @Test
    @DisplayName("fromJsonList 空输入返回不可变空列表（真实行为：静默当作零条，不报错）")
    void should_returnEmptyList_when_listJsonBlank() {
        // Given/When
        List<Sample> nullIn = JsonUtils.fromJsonList(null, Sample.class);
        List<Sample> blankIn = JsonUtils.fromJsonList("  ", Sample.class);

        // Then
        assertEquals(Collections.emptyList(), nullIn);
        assertEquals(0, blankIn.size());
        assertThrows(UnsupportedOperationException.class, () -> nullIn.add(new Sample()));
    }

    @Test
    @DisplayName("fromJsonList 坏 JSON 仍然抛，不会被空列表口径吞掉")
    void should_throwRuntime_when_listJsonMalformed() {
        // Given/When
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonUtils.fromJsonList("[1,", Sample.class));

        // Then
        assertEquals("JSON反序列化失败", ex.getMessage());
    }

    @Test
    @DisplayName("fromJsonToMap 平铺键值，数字保留 Jackson 的原生装箱")
    void should_readMap_when_objectJsonGiven() {
        // Given
        String json = "{\"a\":1,\"b\":\"x\",\"c\":null}";

        // When
        Map<String, Object> map = JsonUtils.fromJsonToMap(json);

        // Then
        assertEquals(3, map.size());
        assertEquals(1, map.get("a"));
        assertEquals("x", map.get("b"));
        assertTrue(map.containsKey("c"));
        assertNull(map.get("c"));
    }

    @Test
    @DisplayName("fromJsonToMap 空输入返回不可变空 Map（掩盖数据丢失的那条 fallback，钉住现状）")
    void should_returnEmptyMap_when_mapJsonBlank() {
        // Given/When
        Map<String, Object> map = JsonUtils.fromJsonToMap("");

        // Then
        assertEquals(Collections.emptyMap(), map);
        assertThrows(UnsupportedOperationException.class, () -> map.put("k", "v"));
    }

    @Test
    @DisplayName("fromJsonToMap 坏 JSON 抛 RuntimeException")
    void should_throwRuntime_when_mapJsonMalformed() {
        // Given/When
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonUtils.fromJsonToMap("not-json"));

        // Then
        assertEquals("JSON反序列化失败", ex.getMessage());
    }

    @Test
    @DisplayName("toMap 把 POJO 摊平成键值对，null 输入给空 Map")
    void should_convertObjectToMap_when_beanGiven() {
        // Given/When
        Map<String, Object> map = JsonUtils.toMap(new Sample(3L, "carol"));
        Map<String, Object> nullMap = JsonUtils.toMap(null);

        // Then
        assertEquals(3L, map.get("id"));
        assertEquals("carol", map.get("name"));
        assertEquals(Collections.emptyMap(), nullMap);
    }

    @Test
    @DisplayName("toMap 转换不了时抛 RuntimeException(\"对象转换失败\")而不是返回空 Map")
    void should_throwRuntime_when_convertValueImpossible() {
        // Given/When
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonUtils.toMap("这不是对象"));

        // Then
        assertEquals("对象转换失败", ex.getMessage());
        assertTrue(ex.getCause() instanceof IllegalArgumentException, String.valueOf(ex.getCause()));
    }

    @Test
    @DisplayName("isValidJson 对 null/空白一律 false，合法标量与容器一律 true")
    void should_answerBySyntax_when_isValidJson() {
        // Given/When/Then
        assertFalse(JsonUtils.isValidJson(null));
        assertFalse(JsonUtils.isValidJson(""));
        assertFalse(JsonUtils.isValidJson(" \t "));
        assertTrue(JsonUtils.isValidJson("{}"));
        assertTrue(JsonUtils.isValidJson("[]"));
        assertTrue(JsonUtils.isValidJson("\"text\""));
        assertTrue(JsonUtils.isValidJson("123"));
    }

    @Test
    @DisplayName("isValidJson 拒截断与裸词，但把 JSON 字面量 null 当作合法文档")
    void should_reportActualVerdict_when_edgeDocuments() {
        // Given/When/Then
        assertFalse(JsonUtils.isValidJson("[1,2"));
        assertFalse(JsonUtils.isValidJson("abc"));
        assertTrue(JsonUtils.isValidJson("null"));
    }

    @Test
    @DisplayName("getObjectMapper 暴露的是同一个共享实例（全站可被一处改配置影响）")
    void should_exposeSharedSingleton_when_mapperRequested() {
        // Given/When
        ObjectMapper first = JsonUtils.getObjectMapper();
        ObjectMapper second = JsonUtils.getObjectMapper();

        // Then
        assertSame(first, second);
        assertFalse(first.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(first.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        assertTrue(first.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));
    }
}
