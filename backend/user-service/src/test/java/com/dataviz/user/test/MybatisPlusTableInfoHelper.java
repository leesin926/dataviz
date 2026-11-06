package com.dataviz.user.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单元测试里没有 Spring/MyBatis 上下文，MyBatis-Plus 的 LambdaWrapper
 * 解析方法引用时会去 TableInfoHelper 的缓存里找列名；不初始化会抛
 * "can not find lambda cache for this entity"。
 * <p>
 * {@code mapUnderscoreToCamelCase} 要显式打开：MyBatis 的 Configuration 默认 false，而生产链路里
 * MyBatis-Plus 默认 true。不打开时 {@code getSqlSegment()} 会按属性名（tenantId）而不是列名
 * （tenant_id）渲染，断言就对不上真实 SQL —— 本模块 listUsers/createUser 的用例正断言这些列名。
 * 与 datasource/alert/collab/monitor/openapi 侧同名助手是同一份复制（第 5 份）。
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
