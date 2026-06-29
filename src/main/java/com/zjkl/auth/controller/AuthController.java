package com.zjkl.auth.controller;

import com.zjkl.auth.dto.BindEmailRequest;
import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.auth.dto.LoginRequest;
import com.zjkl.auth.dto.RefreshTokenRequest;
import com.zjkl.auth.dto.SendCodeRequest;
import com.zjkl.auth.dto.WxLoginRequest;
import com.zjkl.auth.service.AuthService;
import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.common.util.HashUtil;
import com.zjkl.common.util.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final long SEND_CODE_WINDOW_MS = 60_000;
    private static final int SEND_CODE_MAX = 1;
    private static final long SEND_CODE_GLOBAL_WINDOW_MS = 60_000;
    private static final int SEND_CODE_GLOBAL_MAX = 60;

    private static final long LOGIN_WINDOW_MS = 60_000;
    private static final int LOGIN_MAX = 5;
    private static final long REFRESH_WINDOW_MS = 60_000;
    private static final int REFRESH_MAX = 3;
    private static final long COMPLETE_PROFILE_WINDOW_MS = 60_000;
    private static final int COMPLETE_PROFILE_MAX = 5;
    private static final long WX_LOGIN_WINDOW_MS = 60_000;
    private static final int WX_LOGIN_MAX = 5;
    private static final long BIND_EMAIL_WINDOW_MS = 60_000;
    private static final int BIND_EMAIL_MAX = 5;

    private final AuthService authService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    private final EndpointMetrics endpointMetrics;

    public AuthController(AuthService authService,
                          UserContext userContext,
                          RateLimiter rateLimiter,
                          EndpointMetrics endpointMetrics) {
        this.authService = authService;
        this.userContext = userContext;
        this.rateLimiter = rateLimiter;
        this.endpointMetrics = endpointMetrics;
    }

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody @Valid SendCodeRequest request) {
        return endpointMetrics.recordResult("web", "auth.send_code", () -> {
            if (!rateLimiter.tryAcquire("rate:send-code:global", SEND_CODE_GLOBAL_MAX, SEND_CODE_GLOBAL_WINDOW_MS)) {
                return Result.rateLimited("系统繁忙，请稍后再试");
            }
            String rateKey = "rate:send-code:" + request.email();
            if (!rateLimiter.tryAcquire(rateKey, SEND_CODE_MAX, SEND_CODE_WINDOW_MS)) {
                return Result.rateLimited("请求过于频繁，请 1 分钟后再试");
            }
            authService.sendCode(request.email());
            return Result.success();
        });
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        return endpointMetrics.recordResult("web", "auth.login", () -> {
            String rateKey = "rate:login:" + request.email();
            if (!rateLimiter.tryAcquire(rateKey, LOGIN_MAX, LOGIN_WINDOW_MS)) {
                return Result.rateLimited("登录尝试过于频繁，请 1 分钟后再试");
            }
            Map<String, Object> result = authService.login(request);
            return Result.success(result);
        });
    }

    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody @Valid WxLoginRequest request) {
        return endpointMetrics.recordResult("miniprogram", "auth.wx_login", () -> {
            String rateKey = "rate:wx-login:" + hashToken(request.code());
            if (!rateLimiter.tryAcquire(rateKey, WX_LOGIN_MAX, WX_LOGIN_WINDOW_MS)) {
                return Result.rateLimited("微信登录过于频繁，请稍后再试");
            }
            Map<String, Object> result = authService.wxLogin(request.code());
            return Result.success(result);
        });
    }

    @PostMapping("/bind-email")
    public Result<Map<String, Object>> bindEmail(@RequestBody @Valid BindEmailRequest request) {
        return endpointMetrics.recordResult("miniprogram", "auth.bind_email", () -> {
            String rateKey = "rate:bind-email:" + hashToken(request.bindToken());
            if (!rateLimiter.tryAcquire(rateKey, BIND_EMAIL_MAX, BIND_EMAIL_WINDOW_MS)) {
                return Result.rateLimited("绑定过于频繁，请稍后再试");
            }
            Map<String, Object> result = authService.bindEmail(request);
            return Result.success(result);
        });
    }

    @PostMapping("/sync-email-account")
    public Result<Map<String, Object>> syncEmailAccount() {
        return endpointMetrics.recordResult("web", "auth.sync_email_account", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            Map<String, Object> result = authService.syncEmailAccount(userId);
            return Result.success(result);
        });
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return endpointMetrics.recordResult("shared", "auth.refresh", () -> {
            String tokenHash = hashToken(request.refreshToken());
            String rateKey = "rate:refresh:" + tokenHash;
            if (!rateLimiter.tryAcquire(rateKey, REFRESH_MAX, REFRESH_WINDOW_MS)) {
                return Result.rateLimited("刷新过于频繁，请稍后再试");
            }
            Map<String, Object> result = authService.refreshToken(request.refreshToken());
            return Result.success(result);
        });
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestBody(required = false) Map<String, String> params) {
        return endpointMetrics.recordResult("shared", "auth.logout", () -> {
            String userId = userContext.getUserId();
            String refreshToken = params != null ? params.get("refreshToken") : null;
            String accessToken = params != null ? params.get("accessToken") : null;
            authService.logout(userId, refreshToken, accessToken);
            return Result.success();
        });
    }

    @PostMapping("/complete-profile")
    public Result<Void> completeProfile(@RequestBody @Valid CompleteProfileRequest request) {
        return endpointMetrics.recordResult("shared", "auth.complete_profile", () -> {
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
        });
    }

    private static String hashToken(String token) {
        return HashUtil.sha256Hex(token).substring(0, 16);
    }
}
