package com.dataviz.etl.engine;

import com.dataviz.etl.engine.operator.Operator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory and registry for all ETL operators.
 * <p>
 * Collects all Operator beans from the Spring context and provides
 * lookup by type and schema generation for the frontend.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorFactory {

    private final List<Operator> operators;

    private final Map<String, Operator> operatorMap = new ConcurrentHashMap<>();

    /**
     * 设计器与演示数据里的节点名 → 算子类型名。
     * <p>
     * 两边各写各的早就该统一，但现在收不了：已经存进 transform_config 的 DAG 用的就是
     * input / output，改名等于让存量任务全部报「未知算子」。所以在这里做一次映射，
     * 且<b>只在这一处</b>映射 —— 算子内部再各自认别名的话，同一个节点名就有两套生效路径。
     */
    private static final Map<String, String> NODE_TYPE_ALIASES = new HashMap<>();

    static {
        NODE_TYPE_ALIASES.put("input", "database_input");
        NODE_TYPE_ALIASES.put("source", "database_input");
        NODE_TYPE_ALIASES.put("output", "database_output");
        NODE_TYPE_ALIASES.put("sink", "database_output");
    }

    /**
     * Initialize the operator registry after injection.
     */
    @javax.annotation.PostConstruct
    public void init() {
        for (Operator op : operators) {
            operatorMap.put(op.getType(), op);
            log.info("Registered ETL operator: {}", op.getType());
        }
    }

    /**
     * Get an operator by its type, accepting designer node aliases.
     */
    public Operator getOperator(String type) {
        String canonical = canonicalType(type);
        Operator operator = operatorMap.get(canonical);
        if (operator == null) {
            throw new IllegalArgumentException("Unknown operator type: " + type
                    + "（可用算子: " + String.join(", ", listOperatorTypes()) + "）");
        }
        return operator;
    }

    /** Node type → registered operator type; unknown names come back unchanged. */
    public String canonicalType(String type) {
        if (type == null) {
            return null;
        }
        String key = type.trim().toLowerCase(Locale.ROOT);
        String alias = NODE_TYPE_ALIASES.get(key);
        return alias != null ? alias : key;
    }

    /**
     * List all registered operator types.
     */
    public List<String> listOperatorTypes() {
        return operatorMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Get the configuration schema for an operator type.
     * Returns a map describing the expected configuration parameters.
     */
    public Map<String, Object> getOperatorSchema(String type) {
        Map<String, Object> result;
        switch (canonicalType(type)) {
            case "database_input": {
                Map<String, Object> config = new HashMap<>();
                config.put("datasourceId", mapOf("type", "number", "required", true, "label", "Datasource"));
                config.put("tableName", mapOf("type", "string", "required", true, "label", "Table Name"));
                config.put("columns", mapOf("type", "string", "required", false, "label", "Columns (comma-separated)"));
                config.put("whereClause", mapOf("type", "string", "required", false, "label", "WHERE Clause"));
                config.put("limit", mapOf("type", "number", "required", false, "label", "Row Limit"));

                result = new HashMap<>();
                result.put("type", "database_input");
                result.put("name", "Database Input");
                result.put("description", "Read data from a database table");
                result.put("config", config);
                break;
            }
            case "database_output": {
                Map<String, Object> config = new HashMap<>();
                config.put("datasourceId", mapOf("type", "number", "required", true, "label", "Datasource"));
                config.put("tableName", mapOf("type", "string", "required", true, "label", "Table Name"));
                config.put("columns", mapOf("type", "string", "required", false,
                        "label", "Columns (comma-separated, default = 上游列)"));

                Map<String, Object> writeMode = new HashMap<>();
                writeMode.put("type", "select");
                writeMode.put("required", false);
                writeMode.put("label", "Write Mode");
                // 只列 append：overwrite/upsert 现在会明确报错，把它们放进选项里等于提供一个跑不通的下拉项
                writeMode.put("options", Arrays.asList("append"));
                config.put("writeMode", writeMode);

                result = new HashMap<>();
                result.put("type", "database_output");
                result.put("name", "Database Output");
                result.put("description", "Append rows into a database table");
                result.put("config", config);
                break;
            }
            case "filter": {
                Map<String, Object> config = new HashMap<>();
                config.put("field", mapOf("type", "string", "required", true, "label", "Field"));

                Map<String, Object> operatorField = new HashMap<>();
                operatorField.put("type", "select");
                operatorField.put("required", true);
                operatorField.put("options", Arrays.asList("eq", "ne", "gt", "lt", "gte", "lte", "contains", "in"));
                config.put("operator", operatorField);

                config.put("value", mapOf("type", "any", "required", true, "label", "Value"));

                result = new HashMap<>();
                result.put("type", "filter");
                result.put("name", "Filter");
                result.put("description", "Filter rows based on conditions");
                result.put("config", config);
                break;
            }
            case "sql": {
                Map<String, Object> config = new HashMap<>();
                config.put("sql", mapOf("type", "text", "required", true, "label", "SQL Expression"));
                config.put("outputColumns", mapOf("type", "string", "required", false, "label", "Output Columns"));

                result = new HashMap<>();
                result.put("type", "sql");
                result.put("name", "SQL Transform");
                result.put("description", "Apply SQL transformation");
                result.put("config", config);
                break;
            }
            case "aggregate": {
                Map<String, Object> config = new HashMap<>();
                config.put("groupByFields", mapOf("type", "string", "required", false, "label", "Group By Fields"));

                Map<String, Object> items = new HashMap<>();
                items.put("field", mapOf("type", "string"));

                Map<String, Object> functionField = new HashMap<>();
                functionField.put("type", "select");
                functionField.put("options", Arrays.asList("sum", "avg", "count", "min", "max"));
                items.put("function", functionField);

                items.put("alias", mapOf("type", "string"));

                Map<String, Object> aggregations = new HashMap<>();
                aggregations.put("type", "array");
                aggregations.put("required", true);
                aggregations.put("label", "Aggregations");
                aggregations.put("items", items);
                config.put("aggregations", aggregations);

                result = new HashMap<>();
                result.put("type", "aggregate");
                result.put("name", "Aggregate");
                result.put("description", "Perform aggregation on data");
                result.put("config", config);
                break;
            }
            default:
                result = new HashMap<>();
                result.put("type", type);
                result.put("name", type);
                result.put("description", "Unknown operator");
                break;
        }
        return result;
    }

    /**
     * Helper to create a map from alternating key-value pairs.
     */
    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
