package com.dataviz.openapi.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单测没有 Spring/MyBatis 上下文，LambdaWrapper 解析方法引用时要查
 * TableInfoHelper 的列名缓存，不初始化会抛 "can not find lambda cache for this entity"。
 * 与 collab-service 侧同名助手是一份复制：跨模块共享 test-jar 的收益不抵多一个 Maven 依赖。
 */
public final class MybatisPlusTableInfoHelper {

    private MybatisPlusTableInfoHelper() {
    }

    public static void initFor(Class<?>... entityClasses) {
        Configuration configuration = new Configuration();
        // 裸 MyBatis Configuration 默认关下划线转驼峰，getSqlSegment() 会吐出属性名而不是列名，
        // 断言列名的用例会假失败；MyBatis-Plus 生产环境的默认值是开启。
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> clazz : entityClasses) {
            TableInfoHelper.initTableInfo(assistant, clazz);
        }
    }
}
