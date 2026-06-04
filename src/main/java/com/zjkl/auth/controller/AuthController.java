package com.zjkl.auth.controller;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.dto.RefreshTokenRequest;
import com.zjkl.auth.dto.SendCodeRequest;
import com.zjkl.auth.service.AuthService;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.util.HashUtil;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final long SEND_CODE_WINDOW_MS = 60_000; // 1 分钟窗口
    private static final int SEND_CODE_MAX = 1;              // 每分钟每邮箱最多 1 次
    private static final long SEND_CODE_GLOBAL_WINDOW_MS = 60_000; // 1 分钟窗口
    private static final int SEND_CODE_GLOBAL_MAX = 60;             // 全局每分钟最多 60 次（防邮件轰炸）

    private static final long LOGIN_WINDOW_MS = 60_000;    // 1 分钟窗口
    private static final int LOGIN_MAX = 5;                 // 每分钟每邮箱最多 5 次
    private static final long REFRESH_WINDOW_MS = 60_000;   // 1 分钟窗口
    private static final int REFRESH_MAX = 3;               // 每分钟最多 3 次

    private static final long COMPLETE_PROFILE_WINDOW_MS = 60_000;
    private static final int COMPLETE_PROFILE_MAX = 5;

    private final AuthService authService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, UserContext userContext, RateLimiter rateLimiter) {
        this.authService = authService;
        this.userContext = userContext;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody @Valid SendCodeRequest request) {
        // 全局限流：防止邮件轰炸（二级保护）
        if (!rateLimiter.tryAcquire("rate:send-code:global", SEND_CODE_GLOBAL_MAX, SEND_CODE_GLOBAL_WINDOW_MS)) {
            return Result.rateLimited("系统繁忙，请稍后再试");
        }
        // 每邮箱限流
        String rateKey = "rate:send-code:" + request.email();
        if (!rateLimiter.tryAcquire(rateKey, SEND_CODE_MAX, SEND_CODE_WINDOW_MS)) {
            return Result.rateLimited("请求过于频繁，请 1 分钟后再试");
        }
        authService.sendCode(request.email());
        return Result.success();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        // 限流：每邮箱每分钟最多 5 次登录尝试
        String rateKey = "rate:login:" + request.email();
        if (!rateLimiter.tryAcquire(rateKey, LOGIN_MAX, LOGIN_WINDOW_MS)) {
            return Result.rateLimited("登录尝试过于频繁，请 1 分钟后再试");
        }
        Map<String, Object> result = authService.login(request);
        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        // 限流：每客户端每分钟最多 3 次刷新（使用 token hash 作为 key，避免碰撞）
        String token = request.refreshToken();
        String tokenHash = hashToken(token);
        String rateKey = "rate:refresh:" + tokenHash;
        if (!rateLimiter.tryAcquire(rateKey, REFRESH_MAX, REFRESH_WINDOW_MS)) {
            return Result.rateLimited("刷新过于频繁，请稍后再试");
        }
        Map<String, Object> result = authService.refreshToken(request.refreshToken());
        return Result.success(result);
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestBody(required = false) Map<String, String> params) {
        String userId = userContext.getUserId();
        String refreshToken = params != null ? params.get("refreshToken") : null;
        String accessToken = params != null ? params.get("accessToken") : null;
        authService.logout(userId, refreshToken, accessToken);
        return Result.success();
    }

    @PostMapping("/complete-profile")
    public Result<Void> completeProfile(@RequestBody @Valid CompleteProfileRequest request) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        String rateKey = "rate:complete-profile:" + userId;
        if (!rateLimiter.tryAcquire(rateKey, COMPLETE_PROFILE_MAX, COMPLETE_PROFILE_WINDOW_MS)) {
            return Result.rateLimited("操作过于频繁，请稍后再试");
        }
        authService.completeProfile(userId, request);
        return Result.success();
    }

    /**
     * 对 token 做 SHA-256 摘要，取前 16 位十六进制作为限流 key
     */
    private static String hashToken(String token) {
        return HashUtil.sha256Hex(token).substring(0, 16);
    }
}
