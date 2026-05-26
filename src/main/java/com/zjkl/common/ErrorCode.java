package com.zjkl.common;

/**
 * 统一错误码枚举
 */
public enum ErrorCode {

    // ==================== 通用 ====================
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    TIMEOUT(504, "服务超时，请稍后重试"),

    // ==================== 认证模块 (1000) ====================
    AUTH_LOGIN_FAILED(1001, "登录失败，账号或密码错误"),
    AUTH_TOKEN_EXPIRED(1002, "登录已过期，请重新登录"),
    AUTH_TOKEN_INVALID(1003, "无效的登录凭证"),
    AUTH_CODE_SEND_FAILED(1004, "验证码发送失败"),
    AUTH_CODE_INVALID(1005, "验证码错误或已过期"),

    // ==================== 情感引擎 (2000) ====================
    EMOTION_LOCK_FAILED(2001, "情感状态更新冲突，请稍后重试"),
    EMOTION_ANCHOR_NOT_FOUND(2002, "情感锚点不存在"),
    EMOTION_PERSONALITY_INVALID(2003, "人格参数无效"),
    EMOTION_UPDATE_FAILED(2004, "情感状态更新失败"),

    // ==================== 语音合成 (3000) ====================
    VOICE_SYNTHESIS_FAILED(3001, "语音合成失败"),
    VOICE_PARAMS_INVALID(3002, "语音参数无效"),

    // ==================== AI 服务 (4000) ====================
    AI_LLM_CALL_FAILED(4001, "AI 模型调用失败"),
    AI_IMAGE_GENERATION_FAILED(4002, "图片生成失败"),
    AI_IMAGE_UNDERSTANDING_FAILED(4003, "图片理解失败"),
    AI_STREAM_PARSE_FAILED(4004, "AI 响应解析失败"),

    // ==================== 存储/OSS (5000) ====================
    OSS_UPLOAD_FAILED(5001, "文件上传失败"),
    OSS_DOWNLOAD_FAILED(5002, "文件下载失败"),
    OSS_FILE_NOT_FOUND(5003, "文件不存在"),

    // ==================== 用户 (6000) ====================
    USER_NOT_FOUND(6001, "用户不存在"),
    USER_PROFILE_INCOMPLETE(6002, "用户资料不完整"),
    USER_REGISTER_FAILED(6003, "注册失败"),

    // ==================== 推荐引擎 (7000) ====================
    RECOMMENDATION_FAILED(7001, "推荐生成失败"),

    // ==================== 记忆/摘要 (8000) ====================
    SUMMARY_GENERATION_FAILED(8001, "摘要生成失败"),
    MEMORY_RETRIEVAL_FAILED(8002, "记忆检索失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Result<Void> toResult() {
        return Result.error(code, message);
    }

    public <T> Result<T> toResult(T data) {
        Result<T> result = Result.error(code, message);
        result.setData(data);
        return result;
    }
}
