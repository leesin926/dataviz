package com.dataviz.common.log.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * <p>
 * 标注在Controller方法上，自动记录操作日志。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作类型（如：新增、修改、删除、查询、导入、导出）
     */
    OperType type() default OperType.OTHER;

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean recordResult() default false;

    /**
     * 操作类型枚举
     */
    enum OperType {
        /** 新增 */
        INSERT("新增"),
        /** 修改 */
        UPDATE("修改"),
        /** 删除 */
        DELETE("删除"),
        /** 查询 */
        SELECT("查询"),
        /** 导入 */
        IMPORT("导入"),
        /** 导出 */
        EXPORT("导出"),
        /** 授权 */
        GRANT("授权"),
        /** 登录 */
        LOGIN("登录"),
        /** 登出 */
        LOGOUT("登出"),
        /** 其他 */
        OTHER("其他");

        private final String description;

        OperType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
