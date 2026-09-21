package com.dataviz.common.core.util;

import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import cn.hutool.core.util.StrUtil;

import java.util.Collection;
import java.util.Objects;

/**
 * 断言工具类
 */
public final class AssertUtil {

    private AssertUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 断言对象不为null
     */
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言对象不为null
     */
    public static void notNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断言字符串不为空
     */
    public static void notBlank(String str, String message) {
        if (StrUtil.isBlank(str)) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言字符串不为空
     */
    public static void notBlank(String str, ErrorCode errorCode) {
        if (StrUtil.isBlank(str)) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断言集合不为空
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言表达式为true
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言表达式为true
     */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断言表达式为false
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言两个对象相等
     */
    public static void equals(Object a, Object b, String message) {
        if (!Objects.equals(a, b)) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 断言直接抛出异常
     */
    public static void fail(String message) {
        throw new BizException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 断言直接抛出异常
     */
    public static void fail(ErrorCode errorCode) {
        throw new BizException(errorCode);
    }
}
