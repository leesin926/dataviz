package com.dataviz.common.mybatis.shard;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 分片核心装配。整类由 {@code shard.enabled} 门控，默认关。
 *
 * <h3>{@code shard.enabled=false}（默认）时</h3>
 * <p>
 * 本类一个 bean 都不建：{@link ShardProperties} 不绑定（也就不校验）、不产出 {@code DataSource}、
 * 不碰 {@link MybatisPlusInterceptor} 的拦截器列表。今天 17 个模块与约 926 个测试跑的就是这条路径，
 * 所以这里的原则是<b>关掉即等于不存在</b>，不是"关掉但行为等价"。
 *
 * <h3>数据源</h3>
 * <p>
 * 打开后本类是唯一的 {@code DataSource} 提供者：组件扫描到的 {@code @Configuration} 里声明的 bean
 * 先于自动配置的条件判定，所以 Boot 的 {@code DataSourceAutoConfiguration} 与 classpath 上
 * druid-starter 的 {@code DruidDataSourceAutoConfigure}（两者都是 {@code @ConditionalOnMissingBean(DataSource)}）
 * 都会退让，{@code spring.datasource.*} 那套单库配置随之失效——这是有意的：留两套入口迟早出现
 * "连接池走 A、SQL 走 B"。仍然加 {@code @Primary}，是为了有服务以后自己声明数据源时，
 * MyBatis 拿到的还是分片路由源而不是"两个候选，随 Spring 高兴"。
 *
 * <h3>拦截器链的顺序</h3>
 * <p>
 * 打开后的最终顺序（{@link #orderedChain} 负责拼）：
 * <ol>
 *   <li>{@link ShardInnerInterceptor} —— 闸门 + 落定路由状态</li>
 *   <li>{@code TenantLineInnerInterceptor}（{@code MybatisPlusConfig} 原有）</li>
 *   <li>{@code PaginationInnerInterceptor}（原有）</li>
 *   <li>{@code DynamicTableNameInnerInterceptor}（挂 {@link ShardTableNameHandler}）—— 最后改写表名</li>
 * </ol>
 * 两条独立的理由，缺一条就会出事：
 * <ul>
 *   <li><b>闸门必须在改写器之前</b>：MP 按注册顺序执行 {@code beforeQuery}，闸门先跑才能把
 *       "分片表未绑定上下文"报成那句人话；顺序反了，未绑定语句先按逻辑名放行，报错变成
 *       {@code Table 'audit_log' doesn't exist}，真实原因被盖掉。</li>
 *   <li><b>改写器必须在 {@code TenantLine} 之后</b>：租户插件用
 *       {@code MybatisPlusConfig.IGNORE_TENANT_TABLES} 按<b>表名</b>判断要不要注入 {@code tenant_id}，
 *       那张黑名单记的是逻辑名（{@code alert_notify_log}、{@code openapi_log}、{@code monitor_metric}）。
 *       若先把它改成 {@code alert_notify_log_202611}，黑名单查不中，租户条件会被注到一张<b>没有
 *       {@code tenant_id} 列</b>的表上——症状正是 {@code Unknown column 'tenant_id' in 'where clause'}。
 *       分页插件同理：它要在逻辑名上算 count SQL 的表名。</li>
 * </ul>
 * 实现上不改 {@code MybatisPlusConfig} 一行：所有分片 bean 都带条件，关掉时那个类连被加载的必要都没有；
 * 打开时由 {@link #shardChainWiring} 在单例创建阶段把两个 inner 插进既有链
 * （{@code MybatisPlusInterceptor} 每次执行都现读自己的列表，所以事后换列表对已建好的
 * {@code SqlSessionFactory} 同样生效）。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShardProperties.class)
@ConditionalOnProperty(name = "shard.enabled", havingValue = "true")
public class ShardConfiguration {

    @Bean
    public ShardRouter shardRouter(ShardProperties properties) {
        properties.validate();
        return new ShardRouter(properties);
    }

    /**
     * 多实例路由数据源，替换自动配置出来的单库数据源（见类注释"数据源"）。
     */
    @Bean
    @Primary
    public DataSource shardDataSource(ShardProperties properties, ShardRouter router) {
        ShardRoutingDataSource routing = new ShardRoutingDataSource(properties, router);
        routing.afterPropertiesSet();
        return routing;
    }

    @Bean
    public ShardTableNameHandler shardTableNameHandler(ShardRouter router) {
        return new ShardTableNameHandler(router);
    }

    @Bean
    public ShardInnerInterceptor shardInnerInterceptor(ShardProperties properties, ShardRouter router) {
        return new ShardInnerInterceptor(properties, router);
    }

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor(ShardTableNameHandler handler) {
        return new DynamicTableNameInnerInterceptor(handler);
    }

    @Bean
    public ShardQueryFanout shardQueryFanout(ShardRouter router) {
        return new ShardQueryFanout(router);
    }

    /**
     * 月表预建。<b>双重门控</b>：本 bean 只在 {@code shard.precreate.enabled=true} 时存在，
     * 且方法内还要再读一次开关——看门测试查的是"默认关"，光靠条件装配挡不住直接 new 的用法。
     */
    @Bean
    @ConditionalOnProperty(name = "shard.precreate.enabled", havingValue = "true")
    public ShardMonthMaintenance shardMonthMaintenance(ShardProperties properties, ShardRouter router,
                                                       DataSource dataSource, ResourceLoader resourceLoader) {
        return new ShardMonthMaintenance(properties, router, dataSource, resourceLoader);
    }

    // ------------------------------------------------------------------
    // 拦截器链接线
    // ------------------------------------------------------------------

    /**
     * 把闸门与改写器接进 {@code MybatisPlusConfig} 已有的拦截器链。
     * <p>
     * 用 {@code @Bean} 而不是 {@code SmartInitializingSingleton}：依赖关系写在方法参数上，容器保证
     * {@code MybatisPlusInterceptor} 与两个 inner 都先建好；单例又是即时创建的，所以这个接线一定发生在
     * 应用对外提供服务之前，接线失败（缺 {@code MybatisPlusInterceptor}）就是启动期失败。
     * <p>
     * 接线做一次即够：{@link #orderedChain} 幂等，重复调用不会产生第二个闸门或改写器。
     */
    @Bean
    public ShardChainWiring shardChainWiring(MybatisPlusInterceptor interceptor,
                                             ShardInnerInterceptor validator,
                                             DynamicTableNameInnerInterceptor rewriter) {
        List<InnerInterceptor> chain = orderedChain(interceptor.getInterceptors(), validator, rewriter);
        interceptor.setInterceptors(chain);
        log.info("分片拦截器已接入，顺序={}", describe(chain));
        return new ShardChainWiring(chain);
    }

    /** 接线结果的持有者：没有任何行为，只是让上面那个 {@code @Bean} 有明确的创建时机与可断言的产物。 */
    public static final class ShardChainWiring {

        private final List<InnerInterceptor> chain;

        ShardChainWiring(List<InnerInterceptor> chain) {
            this.chain = chain;
        }

        public List<InnerInterceptor> getChain() {
            return chain;
        }
    }

    /**
     * 拼出目标顺序。幂等：已经接入过（重复调用、或有人又调一次）不会产生第二个闸门/改写器。
     *
     * @param current 既有链（{@code MybatisPlusConfig} 给出来的租户 + 分页）
     */
    static List<InnerInterceptor> orderedChain(List<InnerInterceptor> current,
                                               InnerInterceptor validator, InnerInterceptor rewriter) {
        List<InnerInterceptor> chain = new ArrayList<InnerInterceptor>();
        chain.add(validator);
        if (current != null) {
            for (InnerInterceptor existing : current) {
                // 同类的旧实例丢掉：一是幂等，二是防止把闸门放到改写器后面
                if (isA(existing, validator) || isA(existing, rewriter)) {
                    continue;
                }
                chain.add(existing);
            }
        }
        chain.add(rewriter);
        return chain;
    }

    private static boolean isA(InnerInterceptor candidate, InnerInterceptor reference) {
        return candidate != null && reference != null
                && reference.getClass().isAssignableFrom(candidate.getClass());
    }

    private static List<String> describe(List<InnerInterceptor> chain) {
        List<String> names = new ArrayList<String>();
        for (InnerInterceptor inner : chain) {
            names.add(inner.getClass().getSimpleName());
        }
        return names;
    }
}
