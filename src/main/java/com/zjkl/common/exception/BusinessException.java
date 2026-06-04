package com.zjkl.common.exception;

import com.zjkl.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 - 替代直接使用 RuntimeException
 * <p>
 * 使用方式：
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "自定义消息");
 * <p>
 * 注：已移除 BusinessException(String) 构造器，避免绕过 ErrorCode 默认 500。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
