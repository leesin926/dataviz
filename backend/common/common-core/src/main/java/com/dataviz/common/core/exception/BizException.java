package com.dataviz.common.core.exception;

import com.dataviz.common.core.result.ErrorCode;
import lombok.Getter;


/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final ErrorCode errorCode;

    public BizException(String message) {
        super(message);
        this.code = ErrorCode.INTERNAL_SERVER_ERROR.getCode();
        this.errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.INTERNAL_SERVER_ERROR.getCode();
        this.errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
