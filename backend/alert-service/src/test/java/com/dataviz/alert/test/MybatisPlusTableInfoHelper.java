package com.dataviz.alert.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单元测试里没有 Spring/MyBatis 上下文，MyBatis-Plus 的 LambdaWrapper 解析方法引用时
 * 会去 {@link TableInfoHelper} 的静态缓存里找列名，不初始化就抛
 * "can not find lambda cache for this entity"。这里只能真把 TableInfo 建出来：
 * {@code TableInfoHelper} 是静态入口，而本仓库离线构建里没有 {@code mockito-inline}
 * （本地仓库只有 mockito-core / mockito-junit-jupiter），Mockito 拦不住静态方法。
 *
 * <p>{@code mapUnderscoreToCamelCase} 要显式打开 —— 判据是生产配置，不是任何一份历史拷贝：
 * 每个服务的 {@code src/main/resources/application.yml} 都写着
 * {@code mybatis-plus.configuration.map-underscore-to-camel-case: true}，
 * 裸 MyBatis 的 {@link Configuration} 默认却是 false。不打开时 {@code getSqlSegment()}
 * 按属性名（{@code tenantId}）而不是列名（{@code tenant_id}）渲染，跨模块断言互相不可比，
 * 读者也分不清哪个才是生产行为 —— 这正是本助手六份拷贝曾经各自漂移的病灶。</p>
 *
 * <p>alert / openapi / collab / datasource / monitor / user 六个模块各留一份逐字相同的拷贝
 * （只差 {@code package} 行），是有意为之：抽公共 test-jar 要先 {@code install}，
 * 而 {@code .m2} 里的陈旧制品是本仓库最常见的失败源（API-110、接口测试报告 §19.39.4）。
 * 重复是较小的恶，漂移不是。改这里就六份一起改；screen-service 例外，它内联了同样的两行。</p>
 */
public final class MybatisPlusTableInfoHelper {

    private MybatisPlusTableInfoHelper() {
    }

    public static void initFor(Class<?>... entityClasses) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> clazz : entityClasses) {
            TableInfoHelper.initTableInfo(assistant, clazz);
        }
    }
}
