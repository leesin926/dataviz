package com.dataviz.common.mybatis.shard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 分片测试的最小夹具：起一份"两个实例 + 若干规则"的配置，不碰 Spring、不碰数据库。
 * <p>
 * 实例 url 用的是<b>不可能连通</b>的假地址（{@code jdbc:h2} 都没引，纯字符串）——这一层的所有测试都要
 * 在没装 Docker 的机器上跑绿，而 {@link ShardRoutingDataSource} 用的是懒初始化 Hikari 池，
 * 构造与 {@code afterPropertiesSet()} 都不连库，所以假 url 不会让任何测试挂在网络上。
 */
final class ShardTestFixtures {

    /** 只用于证明"口令不进异常与 toString"的哨兵值。 */
    static final String SECRET = "s3cr3t-DO-NOT-LEAK";

    private ShardTestFixtures() {
    }

    static ShardProperties.Instance instance(String id) {
        ShardProperties.Instance instance = new ShardProperties.Instance();
        instance.setId(id);
        instance.setUrl("jdbc:mysql://127.0.0.1:3306/db_x?useSSL=false");
        instance.setUsername("root");
        instance.setPassword(SECRET);
        return instance;
    }

    /** 两个实例（ds0/ds1）+ 给定规则；与生产要用的桶数一致，便于把实测落点直接钉进测试。 */
    static ShardProperties twoInstances(ShardProperties.Rule... rules) {
        return properties(2, rules);
    }

    static ShardProperties properties(int instanceCount, ShardProperties.Rule... rules) {
        ShardProperties properties = new ShardProperties();
        properties.setEnabled(true);
        List<ShardProperties.Instance> instances = new ArrayList<ShardProperties.Instance>();
        for (int i = 0; i < instanceCount; i++) {
            instances.add(instance("ds" + i));
        }
        properties.setInstances(instances);
        properties.setRules(new ArrayList<ShardProperties.Rule>(Arrays.asList(rules)));
        properties.validate();
        return properties;
    }

    /** 默认口径：按月分表 + 按租户分库 + 2 个桶（就是 PI4 要在真库上打开的那组参数）。 */
    static ShardProperties.Rule rule(String table) {
        return rule(table, ShardMode.MONTH, ShardRouteBy.TENANT, 2);
    }

    static ShardProperties.Rule rule(String table, ShardMode mode, ShardRouteBy routeBy, int buckets) {
        ShardProperties.Rule rule = new ShardProperties.Rule();
        rule.setTable(table);
        rule.setMode(mode);
        rule.setRouteBy(routeBy);
        rule.setBuckets(buckets);
        // 配置期规定 DAY 表不许提前建月表（ShardProperties.validateRules），夹具跟着这条规则走，
        // 免得每个测试都要自己想"DAY 时 pre-create-months 该填几"
        rule.setPreCreateMonths(mode == ShardMode.DAY ? 0 : 1);
        return rule;
    }

    static ShardProperties.Rule withTimeColumn(ShardProperties.Rule rule, String column) {
        rule.setTimeColumn(column);
        return rule;
    }

    static ShardProperties.Rule withPreCreateMonths(ShardProperties.Rule rule, int months) {
        rule.setPreCreateMonths(months);
        return rule;
    }

    /** 建一份配置并返回路由器（配置校验在 {@link #properties} 里已经跑过）。 */
    static ShardRouter routerOf(ShardProperties properties) {
        return new ShardRouter(properties);
    }

    /** 单条规则、两个实例的常用组合。 */
    static ShardRouter router(String table) {
        return routerOf(twoInstances(rule(table)));
    }
}
