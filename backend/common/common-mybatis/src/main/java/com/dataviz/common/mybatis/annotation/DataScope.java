package com.dataviz.common.mybatis.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * <p>
 * 用于标注Mapper方法，控制数据访问范围。
 * 支持按部门、用户、租户等维度进行数据隔离。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 部门表的别名
     */
    String deptAlias() default "";

    /**
     * 用户表的别名
     */
    String userAlias() default "";

    /**
     * 权限字符（用于多个角色匹配满足任意一个）
     */
    String permission() default "";

    /**
     * 范围类型：
     * 1 - 全部数据
     * 2 - 本部门数据
     * 3 - 本部门及以下数据
     * 4 - 仅本人数据
     * 5 - 自定义数据
     */
    int scopeType() default 1;
}
