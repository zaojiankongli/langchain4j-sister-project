package com.zjkl.user.controller;

import com.zjkl.common.Result;
import com.zjkl.common.context.UserContext;
import com.zjkl.common.monitoring.EndpointMetrics;
import com.zjkl.common.util.RateLimiter;
import com.zjkl.user.domain.dto.UserProfileUpdateDTO;
import com.zjkl.user.domain.vo.UserProfileVO;
import com.zjkl.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserProfileController {

    private static final long AVATAR_WINDOW_MS = 60_000;
    private static final int AVATAR_MAX = 3;

    private final UserProfileService userProfileService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    private final EndpointMetrics endpointMetrics;

    /**
     * 获取用户完整资料。
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        return endpointMetrics.recordResult("web", "user.profile.get", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            log.info("获取用户 {} 的资料", userId);
            UserProfileVO profile = userProfileService.getProfile(userId);
            return Result.success(profile);
        });
    }

    /**
     * 更新用户资料（综合）。
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        return endpointMetrics.recordResult("web", "user.profile.update", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            log.info("更新用户 {} 的资料", userId);
            userProfileService.updateProfile(userId, dto);
            return Result.success();
        });
    }

    /**
     * 更新基本信息（用户名、性别）。
     */
    @PutMapping("/basic")
    public Result<Void> updateBasic(
            @RequestParam(required = false) @Size(max = 50, message = "用户名不能超过50个字符") String username,
            @RequestParam(required = false) @Min(1) @Max(2) Integer gender) {
        return endpointMetrics.recordResult("web", "user.basic.update", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            log.info("用户 {} 更新基本信息: username={}, gender={}", userId,
                    username != null ? username.replaceAll("[\\r\\n]", "") : null, gender);
            userProfileService.updateBasic(userId, username, gender);
            return Result.success();
        });
    }

    /**
     * 更新兴趣爱好。
     */
    @PutMapping("/hobbies")
    public Result<Void> updateHobbies(
            @RequestParam(required = false) @Size(max = 500, message = "兴趣爱好不能超过500个字符") String hobbies) {
        return endpointMetrics.recordResult("web", "user.hobbies.update", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            log.info("用户 {} 更新爱好", userId);
            userProfileService.updateHobbies(userId, hobbies);
            return Result.success();
        });
    }

    /**
     * 更新 AI 身份类型。
     */
    @PutMapping("/ai-type")
    public Result<Void> updateAIType(@RequestParam(required = false) @Min(1) @Max(6) Integer aiType) {
        return endpointMetrics.recordResult("web", "user.ai_type.update", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            log.info("用户 {} 更新 AI 类型: {}", userId, aiType);
            userProfileService.updateAiType(userId, aiType);
            return Result.success();
        });
    }

    /**
     * 上传头像，限制每分钟最多 3 次。
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return endpointMetrics.recordResult("web", "user.avatar.upload", () -> {
            String userId = userContext.getUserId();
            if (userId == null) {
                return Result.unauthorized("请先登录");
            }
            String rateKey = "rate:avatar:" + userId;
            if (!rateLimiter.tryAcquire(rateKey, AVATAR_MAX, AVATAR_WINDOW_MS)) {
                return Result.error(429, "头像上传过于频繁，请稍后再试");
            }
            log.info("用户上传头像，用户 ID: {}", userId);
            String avatarUrl = userProfileService.uploadAvatar(userId, file);
            return Result.success(avatarUrl);
        });
    }
}
