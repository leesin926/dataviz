package com.dataviz.collab.test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单元测试里没有 Spring/MyBatis 上下文，MyBatis-Plus 的 LambdaWrapper
 * 解析方法引用时会去 TableInfoHelper 的缓存里找列名；不初始化会抛
 * "can not find lambda cache for this entity"。与 alert-service 侧同名助手是一份复制，
 * 跨模块共享 test-jar 的收益不抵多一个 Maven 依赖的代价。
 *
 * <p>{@code mapUnderscoreToCamelCase} 要显式打开：MyBatis 的 Configuration 默认 false，
 * 而生产链路里 MyBatis-Plus 默认 true。不打开时 {@code getSqlSegment()} 会按属性名
 * （applicantId）而不是列名（applicant_id）渲染，断言就对不上真实 SQL。</p>
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
