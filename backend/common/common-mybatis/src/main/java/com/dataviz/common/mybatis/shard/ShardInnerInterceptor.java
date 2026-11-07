package com.dataviz.common.mybatis.shard;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.core.toolkit.TableNameParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * 分片闸门：在 SQL 真正执行前确认"分片表 + 绑定上下文"两件事同时成立，并把路由状态<b>落定</b>。
 *
 * <h3>注册顺序要求（改这里之前先读）</h3>
 * <p>
 * MyBatis-Plus 的 {@code MybatisPlusInterceptor} 按<b>注册顺序</b>依次调用各 {@code InnerInterceptor}
 * 的 {@code willDoQuery}/{@code beforeQuery}（更新路径是 {@code beforeUpdate}）。本拦截器必须注册在
 * {@code DynamicTableNameInnerInterceptor} <b>之前</b>：它先把实例与后缀算好写回
 * {@link ShardContext.Binding}，随后 {@link ShardTableNameHandler} 才读这份状态拼物理表名，
 * 最后 {@link ShardRoutingDataSource} 在同一次调用栈里读同一个 key 取连接。
 * <p>
 * 顺序反过来的症状：未绑定的分片表语句会先被 handler 按<b>逻辑名</b>放行，报错变成
 * {@code Table 'audit_log' doesn't exist}，把真实原因（没声明分片键）盖掉——这正是最难查的那类改动。
 * {@code ShardInnerInterceptorOrderTest} 钉的就是这条顺序。
 *
 * <p>
 * 表名来自 MyBatis-Plus 自带的 {@link TableNameParser}（词法级，只认 FROM/JOIN/INTO/UPDATE 后面的词），
 * 与本层的改写器用的是<b>同一个解析器</b>：判定和改写口径一致，不会出现"闸门没看见、改写看见了"的偏差。
 * 仓库里没有可用的 SQL 值解析器，也不打算靠猜谓词来路由，见 {@link ShardContext} 的类注释。
 */
@Slf4j
public class ShardInnerInterceptor implements InnerInterceptor {

    private final ShardProperties properties;
    private final ShardRouter router;

    public ShardInnerInterceptor(ShardProperties properties, ShardRouter router) {
        this.properties = properties;
        this.router = router;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) {
        guard(ms.getId(), boundSql == null ? null : boundSql.getSql());
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        guard(ms.getId(), sqlOf(ms, parameter));
    }

    /**
     * 核心判定。查询与更新走<b>同一段代码、同一句话</b>，避免两条路径的报错文案漂移。
     */
    void guard(String statementId, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        List<String> sharded = shardedTables(sql, properties);
        if (sharded.isEmpty()) {
            return;
        }
        if (sharded.size() > 1) {
            throw ShardErrors.multipleShardedTables(sql, sharded);
        }
        String table = sharded.get(0);
        ShardProperties.Rule rule = properties.ruleOf(table);
        ShardContext.Binding binding = ShardContext.current();
        if (binding == null || !table.equalsIgnoreCase(binding.getLogicalTable())) {
            throw ShardErrors.unboundContext(table, rule);
        }
        // 状态落定：算一次、写回 Binding，后面的 handler 与数据源读同一份结果，不再各算各的
        router.resolveInstanceId(binding);
        router.resolveSuffix(binding);
        if (log.isDebugEnabled()) {
            log.debug("分片闸门放行 statement={} {}", statementId, binding);
        }
    }

    /**
     * SQL 里出现的、且在 {@code shard.rules} 里有规则的表（去重、按出现顺序）。
     * <p>
     * 静态方法，便于脱离 MyBatis 生命周期单测。
     */
    static List<String> shardedTables(String sql, ShardProperties properties) {
        List<String> found = new ArrayList<String>();
        Collection<String> tables = new TableNameParser(sql).tables();
        for (String raw : tables) {
            String table = ShardTableNameHandler.unquote(raw);
            if (table == null) {
                continue;
            }
            String lower = table.toLowerCase();
            if (properties.ruleOf(lower) != null && !containsIgnoreCase(found, lower)) {
                found.add(lower);
            }
        }
        return found;
    }

    /** 与 {@link #shardedTables} 同一份判定，供数据源与扇出层复用（只问"这张表要不要分片"）。 */
    static Set<String> distinctLowerTables(String sql) {
        Set<String> tables = new LinkedHashSet<String>();
        for (String raw : new TableNameParser(sql).tables()) {
            String table = ShardTableNameHandler.unquote(raw);
            if (table != null) {
                tables.add(table.toLowerCase());
            }
        }
        return tables;
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    private String sqlOf(MappedStatement ms, Object parameter) {
        try {
            BoundSql boundSql = ms.getBoundSql(parameter);
            return boundSql == null ? null : boundSql.getSql();
        } catch (RuntimeException e) {
            // 动态 SQL 拼不出 boundSql 时不在这层报错（MyBatis 自己会更早失败）；
            // 但必须留话，否则"闸门没检查"这件事是静默的。兜底仍在数据源与 MySQL（逻辑分片表不存在）两层。
            log.warn("分片闸门无法取得 statement={} 的 SQL，跳过本条检查：{}", ms.getId(), e.getClass().getSimpleName());
            return null;
        }
    }
}
