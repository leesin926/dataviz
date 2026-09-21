package com.dataviz.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或认证已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 服务端错误 5xx
    INTERNAL_SERVER_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),
    GATEWAY_TIMEOUT(504, "服务调用超时"),

    // 业务错误 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),
    ACCOUNT_DISABLED(1006, "账号已禁用"),

    DATASOURCE_CONNECTION_FAILED(2001, "数据源连接失败"),
    DATASOURCE_NOT_FOUND(2002, "数据源不存在"),
    DATASOURCE_DUPLICATE(2003, "数据源名称重复"),

    SQL_EXECUTION_ERROR(3001, "SQL执行错误"),
    SQL_SYNTAX_ERROR(3002, "SQL语法错误"),

    ETL_TASK_FAILED(4001, "ETL任务执行失败"),
    ETL_TASK_NOT_FOUND(4002, "ETL任务不存在"),

    DASHBOARD_NOT_FOUND(5001, "仪表盘不存在"),
    DASHBOARD_PUBLISH_ERROR(5002, "仪表盘发布失败"),

    SCREEN_RENDER_ERROR(6001, "大屏渲染错误"),

    FILE_UPLOAD_FAILED(7001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(7002, "文件类型不支持"),
    FILE_SIZE_EXCEEDED(7003, "文件大小超限");

    private final int code;
    private final String message;
}
