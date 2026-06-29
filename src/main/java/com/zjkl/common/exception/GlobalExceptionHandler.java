package com.zjkl.common.exception;

import com.zjkl.auth.exception.UnauthorizedException;
import com.zjkl.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.BindException;
import org.springframework.beans.TypeMismatchException;
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

    private static final String AUTH_REQUIRED_MESSAGE = "登录状态已失效，请重新登录";

    // ==================== 400 - 请求参数错误 ====================

    /**
     * Bean Validation 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.badRequest(message);
    }

    /**
     * Bean Validation 约束校验失败（方法参数级别）
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
     * 业务参数校验失败 — 透传业务代码中面向用户的中文提示
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("业务参数错误 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        String message = e.getMessage();
        // 如果消息看起来是框架级别的（含类名、包名等技术信息），则脱敏
        if (message != null && !message.isEmpty() && !message.contains("class ") && !message.contains("package ")) {
            return Result.badRequest(message);
        }
        return Result.badRequest("请求参数不合法，请检查输入");
    }

    /**
     * 非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<?> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("非法状态 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.badRequest("当前服务状态异常，请稍后重试");
    }

    // ==================== 401 - 认证失败 ====================

    @ExceptionHandler(UnauthorizedException.class)
    public Result<?> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        log.warn("认证失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.unauthorized(sanitizeUnauthorizedMessage(e.getMessage()));
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
     * 不支持的媒体类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("不支持的媒体类型 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(415, "不支持的媒体类型，请使用 application/json");
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(TypeMismatchException.class)
    public Result<?> handleTypeMismatch(TypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型不匹配 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(400, "参数类型错误: " + e.getPropertyName());
    }

    /**
     * 数据绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBind(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("数据绑定失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.error(400, message);
    }

    /**
     * 数据完整性违例
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<?> handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("数据完整性违例 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.error(409, "数据冲突，请检查请求数据");
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
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{} {}]: code={}, message={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 兜底
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 [{} {}]", request.getRequestURI(), e);
        return Result.error("服务器内部错误");
    }

    private String sanitizeUnauthorizedMessage(String message) {
        if ("验证码错误".equals(message) || "验证码已过期，请重新获取".equals(message)) {
            return message;
        }
        return AUTH_REQUIRED_MESSAGE;
    }
}
