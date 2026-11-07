package com.dataviz.common.mybatis.shard;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多实例路由数据源：{@code AbstractRoutingDataSource} + {@link ShardContext} 的 ThreadLocal。
 *
 * <h3>拒绝的分工（为什么两层都要挡）</h3>
 * <p>
 * 只有 {@link ShardInnerInterceptor} 看得见 SQL、才知道语句命中的是哪张表，所以"分片表未绑定上下文"
 * 的拒绝必须落在它那里；本类是第二道闸，挡的是<b>绕过拦截器</b>的入口（{@code JdbcTemplate}、
 * 手工拿连接、以后加的批量写入器）：上下文已绑定但解析不出目标实例时，这里抛同一句话
 * （{@link ShardErrors#unboundContext} / {@link ShardRouter#resolveInstanceId}），两层的报错文本一致。
 * <p>
 * <b>绝不静默退回 ds0</b>：租户的行被写进错误的实例，事后按租户 + 时间根本捞不回来，属于数据丢失；
 * 而一条报错日志是可查的。这一条是本层的立身之本。
 * <p>
 * 上下文<b>完全未绑定</b>时走默认实例——这是必须的：非分片表（配置/CRUD 表，占绝大多数语句）本来就没有
 * 分片键，要求绑定等于把所有模块今天的行为改掉。分片表走不到这里，它在拦截器那一层就已经被拒了。
 *
 * <h3>口令</h3>
 * <p>
 * url / username / password 只进 {@link HikariDataSource} 对象，不参与任何字符串拼接；日志与异常只说
 * 实例 id 与表名。本类 {@code toString()} 也被重写，避免有人把数据源整个打进日志时带出连接串。
 */
@Slf4j
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    private final ShardProperties properties;
    private final ShardRouter router;
    private final List<String> instanceIds;

    public ShardRoutingDataSource(ShardProperties properties, ShardRouter router) {
        super();
        if (properties == null || router == null) {
            throw new IllegalArgumentException("properties / router 都不能为空");
        }
        this.properties = properties;
        this.router = router;
        this.instanceIds = properties.instanceIds();
        Map<Object, DataSource> targets = new LinkedHashMap<Object, DataSource>();
        String defaultId = null;
        for (ShardProperties.Instance instance : properties.getInstances()) {
            DataSource dataSource = createPool(instance);
            targets.put(instance.getId(), dataSource);
            if (defaultId == null) {
                defaultId = instance.getId();
            }
        }
        setTargetDataSources(new HashMap<Object, Object>(targets));
        if (defaultId != null) {
            setDefaultTargetDataSource(targets.get(defaultId));
        }
        log.info("分片数据源就绪：实例 {}（不含连接串与口令）", instanceIds);
    }

    /**
     * 每个实例一个独立 Hikari 池。
     * <p>
     * 用 {@code new HikariDataSource()}（无参）+ setter：池在<b>首次 getConnection</b> 时才初始化，
     * 因此构造与 Spring 上下文启动不会去连库——起不来的库要在第一次用到时报错，而不是让整个服务起不来。
     */
    static DataSource createPool(ShardProperties.Instance instance) {
        if (instance == null || instance.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分片实例配置不完整，无法建池");
        }
        ShardProperties.Pool pool = instance.getPool() == null
                ? new ShardProperties.Pool() : instance.getPool();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("shard-" + instance.getId());
        dataSource.setJdbcUrl(instance.getUrl());
        dataSource.setUsername(instance.getUsername());
        dataSource.setPassword(instance.getPassword());
        dataSource.setMaximumPoolSize(pool.getMaximumPoolSize());
        dataSource.setMinimumIdle(pool.getMinimumIdle());
        dataSource.setConnectionTimeout(pool.getConnectionTimeout());
        return dataSource;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        ShardContext.Binding binding = ShardContext.current();
        if (binding == null) {
            // 见类注释：未绑定只可能是非分片表（分片表在拦截器层已拒），落默认实例
            return properties.defaultInstanceId();
        }
        return lookupKeyOf(binding);
    }

    private String lookupKeyOf(ShardContext.Binding binding) {
        String table = binding.getLogicalTable();
        try {
            return router.resolveInstanceId(binding);
        } catch (BizException e) {
            throw e;
        } catch (MybatisPlusException e) {
            // MP 在 getBoundSql 之类路径上会包一层 MybatisPlusException：保留原文，换成统一错误
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "分片表 " + table + " 路由失败: " + e.getMessage());
        } catch (RuntimeException e) {
            throw ShardErrors.unresolvedInstance(table, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 显式询问某张表在当前线程应连到哪个实例；供维护任务、批量写入器与测试直接调用。
     *
     * @throws BizException 表是分片表但线程上没有（可解析的）绑定
     */
    public String lookupKeyFor(String logicalTable) {
        ShardContext.Binding binding = ShardContext.current();
        ShardProperties.Rule rule = properties.ruleOf(logicalTable);
        if (rule == null) {
            return properties.defaultInstanceId();
        }
        if (binding == null || !logicalTable.equalsIgnoreCase(binding.getLogicalTable())) {
            throw ShardErrors.unboundContext(logicalTable, rule);
        }
        return lookupKeyOf(binding);
    }

    public List<String> getInstanceIds() {
        return new ArrayList<String>(instanceIds);
    }

    /** 只报实例 id；连接串与口令不在这里。 */
    @Override
    public String toString() {
        return "ShardRoutingDataSource" + instanceIds;
    }
}
