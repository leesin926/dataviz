package com.dataviz.etl.engine.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DAG 节点配置的取读语义。
 * <p>
 * 设计器把列表存成 JSON 数组，演示数据与手改的 transform_config 存成逗号串；库里两种都真实存在。
 * 只认一种的话，症状是"界面上一切正常，点启动说配置缺字段"。
 */
class NodeContextConfigTest {

    private NodeContext withConfig(Map<String, Object> config) {
        return NodeContext.builder().nodeId("n1").operatorType("input").config(config).build();
    }

    @Test
    @DisplayName("getConfigLong - JSON 数字与数字字符串都认")
    void should_readLong_when_valueIsNumberOrString() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("datasourceId", 12);
        assertThat(withConfig(config).getConfigLong("datasourceId")).isEqualTo(12L);

        config.put("datasourceId", " 12 ");
        assertThat(withConfig(config).getConfigLong("datasourceId")).isEqualTo(12L);
    }

    @Test
    @DisplayName("getConfigLong - 缺失与空白串返回 null，非法文本明确报错")
    void should_returnNullOrThrow_when_valueMissingOrNotANumber() {
        Map<String, Object> config = new LinkedHashMap<>();
        assertThat(withConfig(config).getConfigLong("datasourceId")).isNull();

        config.put("datasourceId", "   ");
        assertThat(withConfig(config).getConfigLong("datasourceId")).isNull();

        config.put("datasourceId", "abc");
        assertThatThrownBy(() -> withConfig(config).getConfigLong("datasourceId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datasourceId 不是合法 ID: abc");
    }

    @Test
    @DisplayName("getConfigList - 数组形状逐项去空白、丢弃空项")
    void should_readList_when_valueIsArray() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("columns", Arrays.asList("id", " ", "amount"));

        List<String> columns = withConfig(config).getConfigList("columns");

        assertThat(columns).containsExactly("id", "amount");
    }

    @Test
    @DisplayName("getConfigList - 逗号串形状与数组形状给出同样结果")
    void should_readList_when_valueIsCommaSeparatedString() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupBy", " region, dt ");

        List<String> fields = withConfig(config).getConfigList("groupBy");

        assertThat(fields).containsExactly("region", "dt");
    }

    @Test
    @DisplayName("getConfigList - 键不存在时返回空列表而不是 null")
    void should_returnEmptyList_when_keyAbsent() {
        assertThat(withConfig(new LinkedHashMap<String, Object>()).getConfigList("columns")).isEmpty();
    }
}
