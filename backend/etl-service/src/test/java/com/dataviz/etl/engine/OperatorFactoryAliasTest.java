package com.dataviz.etl.engine;

import com.dataviz.etl.engine.operator.NodeResult;
import com.dataviz.etl.engine.operator.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 节点类型名 → 算子的映射。
 * <p>
 * 设计器节点叫 input/output，演示数据叫 source/sink，算子注册名是 database_input/database_output。
 * 别名只允许在这一处收：算子内部各自认别名的话，同一个节点名就有两套生效路径。
 */
class OperatorFactoryAliasTest {

    private static final Operator DATABASE_INPUT = stubOperator("database_input");
    private static final Operator DATABASE_OUTPUT = stubOperator("database_output");
    private static final Operator FILTER = stubOperator("filter");

    private OperatorFactory factory;

    private static Operator stubOperator(String type) {
        return new Operator() {
            @Override
            public NodeResult execute(com.dataviz.etl.engine.operator.NodeContext context) {
                return NodeResult.success(Collections.<Map<String, Object>>emptyList(), 0, 0);
            }

            @Override
            public String getType() {
                return type;
            }
        };
    }

    @BeforeEach
    void setUp() {
        factory = new OperatorFactory(Arrays.asList(DATABASE_INPUT, DATABASE_OUTPUT, FILTER));
        factory.init();
    }

    @Test
    @DisplayName("别名 - input/source 落到 database_input，output/sink 落到 database_output")
    void should_mapDesignerAndDemoNodeNames_when_canonicalizing() {
        assertThat(factory.canonicalType("input")).isEqualTo("database_input");
        assertThat(factory.canonicalType("source")).isEqualTo("database_input");
        assertThat(factory.canonicalType("output")).isEqualTo("database_output");
        assertThat(factory.canonicalType("sink")).isEqualTo("database_output");
    }

    @Test
    @DisplayName("别名 - 大小写与首尾空白不影响命中")
    void should_ignoreCaseAndSurroundingSpaces_when_canonicalizing() {
        assertThat(factory.canonicalType(" Output ")).isEqualTo("database_output");
        assertThat(factory.canonicalType("DATABASE_INPUT")).isEqualTo("database_input");
    }

    @Test
    @DisplayName("别名 - 未登记的节点名原样返回（去空白转小写），由查找处报错")
    void should_returnNameUnchanged_when_notAnAlias() {
        assertThat(factory.canonicalType(" Aggregate ")).isEqualTo("aggregate");
        assertThat(factory.canonicalType(null)).isNull();
    }

    @Test
    @DisplayName("查找 - 用设计器节点名拿到的就是对应算子")
    void should_returnSameOperator_when_lookedUpByAlias() {
        assertThat(factory.getOperator("input")).isSameAs(DATABASE_INPUT);
        assertThat(factory.getOperator("output")).isSameAs(DATABASE_OUTPUT);
        assertThat(factory.getOperator("database_output")).isSameAs(DATABASE_OUTPUT);
    }

    @Test
    @DisplayName("查找 - 未知节点名报出可用算子清单，而不是空指针")
    void should_listAvailableOperators_when_typeUnknown() {
        assertThatThrownBy(() -> factory.getOperator("python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown operator type: python")
                .hasMessageContaining("database_input");
    }

    @Test
    @DisplayName("Schema - 用别名 output 也拿得到目标端表单，写入方式只列 append")
    void should_returnSinkSchema_when_queriedByAlias() {
        Map<String, Object> schema = factory.getOperatorSchema("output");

        assertThat(schema).containsEntry("type", "database_output");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) schema.get("config");
        assertThat(config).containsKeys("datasourceId", "tableName", "columns", "writeMode");

        @SuppressWarnings("unchecked")
        Map<String, Object> writeMode = (Map<String, Object>) config.get("writeMode");
        @SuppressWarnings("unchecked")
        List<Object> options = (List<Object>) writeMode.get("options");
        // overwrite/upsert 现在会明确报错，把它们放进下拉选项等于提供一个跑不通的选项
        assertThat(options).containsExactly("append");
    }

    @Test
    @DisplayName("Schema - 未知类型落到兜底分支，不抛异常打断算子面板")
    void should_returnFallbackSchema_when_typeUnknown() {
        Map<String, Object> schema = factory.getOperatorSchema("python");

        assertThat(schema).containsEntry("description", "Unknown operator");
    }
}
