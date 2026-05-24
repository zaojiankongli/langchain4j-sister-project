package com.zjkl.common.exception;

import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.user.domain.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 400 - 请求参数错误 ====================

    /**
     * Bean Validation 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.badRequest(message);
    }

    /**
     * Bean Validation 校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.badRequest(message);
    }

    /**
     * 请求体 JSON 解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体解析失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.badRequest("请求体格式错误，请检查 JSON 格式");
    }

    /**
     * 缺少必需的请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少必需参数 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getParameterName());
        return Result.badRequest("缺少必需参数: " + e.getParameterName());
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件上传超限 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.badRequest("文件大小超过限制");
    }


    /**
     * 业务参数校验失败
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("业务参数错误 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * 非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<?> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("非法状态 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.badRequest(e.getMessage());
    }


    @ExceptionHandler(UnauthorizedException.class)
    public Result<?> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        log.warn("认证失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.unauthorized(e.getMessage());
    }


    /**
     * 资源不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.notFound("接口不存在: " + request.getRequestURI());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(405, "请求方法不支持: " + e.getMethod());
    }


    /**
     * 超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    public Result<?> handleTimeout(TimeoutException e, HttpServletRequest request) {
        log.error("服务调用超时 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(504, "服务繁忙，请稍后重试");
    }

    /**
     * 空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<?> handleNpe(NullPointerException e, HttpServletRequest request) {
        log.error("空指针异常 [{} {}]", request.getRequestURI(), e);
        return Result.error("服务内部错误");
    }

    /**
     * 兜底
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{} {}]", request.getRequestURI(), e);
        return Result.error("服务器内部错误");
    }
}
