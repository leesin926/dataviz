package com.dataviz.common.mybatis.shard;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 拦截器链顺序的钉桩。
 * <p>
 * 顺序错了不会编译失败，也不会在这套测试里失败——它会在生产上把"没绑分片键"报成
 * {@code Table 'alert_event' doesn't exist}，或者把租户条件注到一张没有 {@code tenant_id} 列的
 * 月度表上（{@code Unknown column 'tenant_id' in 'where clause'}）。两条都极难从症状反推回来，
 * 所以这里钉的是<b>顺序本身</b>，而不是症状。
 */
class ShardInnerInterceptorOrderTest {

    private ShardInnerInterceptor validator() {
        ShardProperties properties = ShardTestFixtures.twoInstances(ShardTestFixtures.rule("alert_event"));
        return new ShardInnerInterceptor(properties, ShardTestFixtures.routerOf(properties));
    }

    private DynamicTableNameInnerInterceptor rewriter() {
        return new DynamicTableNameInnerInterceptor(
                new ShardTableNameHandler(ShardTestFixtures.router("alert_event")));
    }

    @Test
    @DisplayName("闸门在最前、表名改写器在最后，原有的租户 + 分页留在中间且相对顺序不变")
    void should_placeValidatorFirstAndRewriterLast_when_chainIsWired() {
        InnerInterceptor tenant = new TenantLineInnerInterceptor();
        InnerInterceptor pagination = new PaginationInnerInterceptor();
        InnerInterceptor validator = validator();
        InnerInterceptor rewriter = rewriter();

        List<InnerInterceptor> chain = ShardConfiguration.orderedChain(
                new ArrayList<InnerInterceptor>(Arrays.asList(tenant, pagination)), validator, rewriter);

        assertThat(chain).containsExactly(validator, tenant, pagination, rewriter);
        assertThat(chain.get(0)).isInstanceOf(ShardInnerInterceptor.class);
        assertThat(chain.get(chain.size() - 1)).isInstanceOf(DynamicTableNameInnerInterceptor.class);
    }

    @Test
    @DisplayName("接线幂等：重复接线不会多出第二个闸门或第二个改写器")
    void should_beIdempotent_when_wiringRunsTwice() {
        InnerInterceptor validator = validator();
        InnerInterceptor rewriter = rewriter();
        InnerInterceptor tenant = new TenantLineInnerInterceptor();

        List<InnerInterceptor> once = ShardConfiguration.orderedChain(
                new ArrayList<InnerInterceptor>(Arrays.asList(tenant)), validator, rewriter);
        List<InnerInterceptor> twice = ShardConfiguration.orderedChain(once, validator, rewriter);

        assertThat(twice).hasSameSizeAs(once);
        assertThat(twice).filteredOn(existing -> validator.getClass().isInstance(existing)).hasSize(1);
        assertThat(twice).filteredOn(existing -> rewriter.getClass().isInstance(existing)).hasSize(1);
    }

    @Test
    @DisplayName("旧的同类型实例被换掉：不会先后出现两个改写器把表名改两遍")
    void should_replaceStaleInstancesOfSameType_when_chainAlreadyHasShardParts() {
        InnerInterceptor oldValidator = validator();
        InnerInterceptor newValidator = validator();
        InnerInterceptor oldRewriter = rewriter();
        InnerInterceptor newRewriter = rewriter();

        List<InnerInterceptor> chain = ShardConfiguration.orderedChain(
                new ArrayList<InnerInterceptor>(Arrays.asList(oldValidator, oldRewriter)),
                newValidator, newRewriter);

        assertThat(chain).containsExactly(newValidator, newRewriter);
        assertThat(chain).doesNotContain(oldValidator, oldRewriter);
    }

    @Test
    @DisplayName("既有链为空/为 null 也能接线（服务自定义过拦截器链的情况）")
    void should_tolerateNullOrEmptyChain_when_noExistingInterceptors() {
        InnerInterceptor validator = validator();
        InnerInterceptor rewriter = rewriter();

        assertThat(ShardConfiguration.orderedChain(null, validator, rewriter))
                .containsExactly(validator, rewriter);
        assertThat(ShardConfiguration.orderedChain(new ArrayList<InnerInterceptor>(), validator, rewriter))
                .containsExactly(validator, rewriter);
    }

    @Test
    @DisplayName("改写器必须在租户插件之后：否则月度表名会让 IGNORE_TENANT_TABLES 的按名判断落空")
    void should_keepRewriterAfterTenantPlugin_when_reasonIsTableName() {
        InnerInterceptor tenant = new TenantLineInnerInterceptor();
        List<InnerInterceptor> chain = ShardConfiguration.orderedChain(
                new ArrayList<InnerInterceptor>(Arrays.asList(tenant)), validator(), rewriter());

        int tenantIndex = chain.indexOf(tenant);
        int rewriterIndex = chain.size() - 1;
        // 这条断言钉的是"为什么"，不只是顺序：改写器一旦提前，租户条件就会注到 _202611 这种没有 tenant_id 黑名单命中的表上
        assertThat(rewriterIndex).isGreaterThan(tenantIndex);
        assertThat(chain.get(0).getClass()).isEqualTo(ShardInnerInterceptor.class);
    }
}
