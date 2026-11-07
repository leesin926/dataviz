package com.dataviz.common.mybatis.shard;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 逻辑表名 → 物理表名。挂在 {@code DynamicTableNameInnerInterceptor} 上。
 * <p>
 * 只有三种情况会被改写：这张表在 {@code shard.rules} 里、线程上有绑定、且绑定的就是这张表。
 * 其余一律<b>原样返回逻辑名</b>——包括 {@code shard.enabled=false}（那时这条链根本不会被注册，
 * 见 {@link ShardConfiguration}），所以今天的 SQL 一个字符都不会变。
 *
 * <h3>注册顺序（改这里之前先读）</h3>
 * <p>
 * 本 handler 读的是 {@link ShardContext} 里的绑定态，而"绑定态是否可用"由
 * {@link ShardInnerInterceptor} 判定。MyBatis-Plus 的 {@code MybatisPlusInterceptor} 按<b>注册顺序</b>
 * 执行 {@code beforeQuery}，所以 {@link ShardInnerInterceptor} 必须注册在
 * {@code DynamicTableNameInnerInterceptor} <b>之前</b>：先校验/落定路由状态，再改写表名。
 * 顺序颠倒的后果是——未绑定的语句会先被改写成 {@code audit_log}（handler 拿不到绑定就原样返回），
 * 报错落在"表不存在"，把真正的原因（没绑分片键）盖掉。
 */
@Slf4j
public class ShardTableNameHandler implements TableNameHandler {

    private final ShardRouter router;

    public ShardTableNameHandler(ShardRouter router) {
        this.router = router;
    }

    @Override
    public String dynamicTableName(String sql, String tableName) {
        String logical = unquote(tableName);
        if (logical == null || !router.isSharded(logical)) {
            // 非分片表：原样返回，连大小写都不动，避免把 "SysTenant" 变成 "systenant"
            return tableName;
        }
        ShardContext.Binding binding = ShardContext.current();
        if (binding == null || !logical.equalsIgnoreCase(safeLower(binding.getLogicalTable()))) {
            // 走到这里说明拦截器没起作用（正常路径不可能出现：它先跑并且会拒绝）。
            // 这里刻意不抛异常：handler 对一条 SQL 会被调用多次，抛在这里会让报错文案变成 MP 的包装栈，
            // 统一拒绝话术归拦截器与数据源两层。
            log.warn("分片表 {} 在未绑定上下文的情况下进入了表名改写，已按逻辑名放行（应由 ShardInnerInterceptor 拦截）",
                    logical);
            return tableName;
        }
        return ShardRouter.physical(logical, router.resolveSuffix(binding));
    }

    private static String safeLower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    /** 去掉 {@code `name`} / {@code "name"} / {@code [name]} 外壳；MP 的表名解析器可能带引号给过来。 */
    static String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() < 2) {
            return value.isEmpty() ? null : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
