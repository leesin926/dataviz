package com.dataviz.monitor.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单测没有 Spring/MyBatis 上下文，LambdaWrapper 解析方法引用时要查
 * TableInfoHelper 的列名缓存，不初始化会抛 "can not find lambda cache for this entity"。
 * 与 openapi/collab 侧同名助手是一份复制：跨模块共享 test-jar 的收益不抵多一个 Maven 依赖。
 *
 * <p>{@code mapUnderscoreToCamelCase} 要显式打开：MyBatis 的 Configuration 默认 false，
 * 而生产链路里 MyBatis-Plus 默认 true。不打开时 {@code getSqlSegment()} 会按属性名而不是
 * 列名渲染，断言就对不上真实 SQL。</p>
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
