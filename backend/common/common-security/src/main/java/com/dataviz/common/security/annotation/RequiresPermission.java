package com.dataviz.common.security.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * <p>
 * 标注在Controller方法上，用于校验用户是否拥有指定权限。
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /**
     * 需要的权限标识
     */
    String[] value();

    /**
     * 逻辑关系：AND（所有权限都要满足）/ OR（满足任意一个即可）
     */
    Logical logical() default Logical.OR;

    /**
     * 逻辑关系枚举
     */
    enum Logical {
        AND,
        OR
    }
}
