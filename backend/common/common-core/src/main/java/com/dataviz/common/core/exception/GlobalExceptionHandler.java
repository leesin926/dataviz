package com.dataviz.common.core.exception;

import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.common.core.result.R;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 [{}]: {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 - @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 缺少必要参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 路径变量/请求参数类型不匹配（如 /api/file/view/abc 打给 Long id）：
     * 不单独处理会落到"其他未知异常"变成 500，掩盖掉"参数非法"这一真实原因。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), "参数格式不正确: " + e.getName());
    }

    /**
     * 请求体反序列化失败（字段类型传错、body 不是合法 JSON）：
     * 与类型不匹配同族，不单独处理会落到"其他未知异常"变成 500 + 整栈，掩盖掉"输入非法"这一真实原因。
     * 详情只进日志，不回显给客户端（Jackson 原文含类名与内部结构）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体不可读 [{}]: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式不正确");
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}]: {}", request.getRequestURI(), e.getMessage(), e);
        return R.fail(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
