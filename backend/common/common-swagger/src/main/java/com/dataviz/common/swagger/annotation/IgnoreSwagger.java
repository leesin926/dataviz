package com.dataviz.common.swagger.annotation;

import java.lang.annotation.*;

/**
 * 忽略Swagger注解
 * <p>
 * 标注在Controller或方法上，使其不出现在API文档中。
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreSwagger {
}
