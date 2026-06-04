package com.zjkl.user.controller;

import com.zjkl.common.context.UserContext;
import com.zjkl.common.Result;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

/**
 * 用户资料控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserProfileController {
    
    private final UserProfileService userProfileService;
    private final UserContext userContext;
    private final RateLimiter rateLimiter;
    
    /**
     * 获取用户完整资料
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        log.info("获取用户 {} 的资料", userId);
        UserProfileVO profile = userProfileService.getProfile(userId);
        return Result.success(profile);
    }
    
    /**
     * 更新用户资料（综合）
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        log.info("更新用户 {} 的资料", userId);
        userProfileService.updateProfile(userId, dto);
        return Result.success();
    }
    
    /**
     * 更新基本信息（用户名、性别）
     */
    @PutMapping("/basic")
    public Result<Void> updateBasic(
            @RequestParam(required = false) @Size(max = 50, message = "用户名不能超过50个字符") String username,
            @RequestParam(required = false) @Min(1) @Max(2) Integer gender) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        log.info("用户 {} 更新基本信息: username={}, gender={}", userId, username, gender);
        userProfileService.updateBasic(userId, username, gender);
        return Result.success();
    }
    
    /**
     * 更新兴趣爱好
     */
    @PutMapping("/hobbies")
    public Result<Void> updateHobbies(
            @RequestParam(required = false) @Size(max = 500, message = "兴趣爱好不能超过500个字符") String hobbies) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        log.info("用户 {} 更新爱好", userId);
        userProfileService.updateHobbies(userId, hobbies);
        return Result.success();
    }
    
    /**
     * 更新 AI 身份类型
     */
    @PutMapping("/ai-type")
    public Result<Void> updateAIType(@RequestParam(required = false) @Min(1) @Max(6) Integer aiType) {
        String userId = userContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        log.info("用户 {} 更新AI类型: {}", userId, aiType);
        userProfileService.updateAiType(userId, aiType);
        return Result.success();
    }
    
    /**
     * 上传头像
     */
    /** 头像上传限流：每分钟最多 3 次 */
    private static final long AVATAR_WINDOW_MS = 60_000;
    private static final int AVATAR_MAX = 3;

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
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
    }
}
