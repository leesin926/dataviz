package com.dataviz.alert.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单元测试里没有 Spring/MyBatis 上下文，MyBatis-Plus 的 LambdaWrapper
 * 解析方法引用时会去 TableInfoHelper 的缓存里找列名；不初始化会抛
 * "can not find lambda cache for this entity"。
 */
public final class MybatisPlusTableInfoHelper {

    private MybatisPlusTableInfoHelper() {
    }

    public static void initFor(Class<?>... entityClasses) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        for (Class<?> clazz : entityClasses) {
            TableInfoHelper.initTableInfo(assistant, clazz);
        }
    }
}
