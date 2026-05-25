package com.zjkl.auth.controller;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.dto.RefreshTokenRequest;
import com.zjkl.auth.dto.SendCodeRequest;
import com.zjkl.auth.service.AuthService;
import com.zjkl.common.context.UserContext;
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
    private static final int SEND_CODE_MAX = 1;              // 每分钟最多 1 次

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
        String rateKey = "rate:send-code:" + request.email();
        if (!rateLimiter.tryAcquire(rateKey, SEND_CODE_MAX, SEND_CODE_WINDOW_MS)) {
            return Result.error(429, "请求过于频繁，请 1 分钟后再试");
        }
        authService.sendCode(request.email());
        return Result.success();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        Map<String, Object> result = authService.login(request);
        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        Map<String, Object> result = authService.refreshToken(request.refreshToken());
        return Result.success(result);
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestBody(required = false) Map<String, String> params) {
        String userId = userContext.getUserId();
        String refreshToken = params != null ? params.get("refreshToken") : null;
        authService.logout(userId, refreshToken);
        return Result.success();
    }

    @PostMapping("/complete-profile")
    public Result<Void> completeProfile(@RequestBody @Valid CompleteProfileRequest request) {
        String userId = userContext.getUserId();
        authService.completeProfile(userId, request);
        return Result.success();
    }
}
