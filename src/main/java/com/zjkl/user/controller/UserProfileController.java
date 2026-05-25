package com.zjkl.user.controller;

import com.zjkl.common.context.UserContext;

import com.zjkl.common.Result;
import com.zjkl.user.domain.dto.UserProfileUpdateDTO;
import com.zjkl.user.domain.vo.UserProfileVO;
import com.zjkl.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {
    
    private final UserProfileService userProfileService;
    private final UserContext userContext;
    
    /**
     * 获取用户完整资料
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        String userId = userContext.getUserId();
        log.info("获取用户 {} 的资料", userId);
        UserProfileVO profile = userProfileService.getProfile(userId);
        return Result.success(profile);
    }
    
    /**
     * 更新用户资料（综合）
     */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserProfileUpdateDTO dto) {
        String userId = userContext.getUserId();
        log.info("更新用户 {} 的资料", userId);
        userProfileService.updateProfile(userId, dto);
        return Result.success();
    }
    
    /**
     * 更新基本信息（用户名、性别）
     */
    @PostMapping("/updateBasic")
    public Result<Void> updateBasic(@RequestBody java.util.Map<String, Object> body) {
        String userId = userContext.getUserId();
        String username = body.get("username") != null ? body.get("username").toString() : null;
        Integer gender = body.get("gender") != null ? Integer.parseInt(body.get("gender").toString()) : null;
        log.info("用户 {} 更新基本信息: username={}, gender={}", userId, username, gender);
        userProfileService.updateBasic(userId, username, gender);
        return Result.success();
    }
    
    /**
     * 更新兴趣爱好
     */
    @PostMapping("/updateHobbies")
    public Result<Void> updateHobbies(@RequestBody java.util.Map<String, Object> body) {
        String userId = userContext.getUserId();
        String hobbies = body.get("hobbies") != null ? body.get("hobbies").toString() : null;
        log.info("用户 {} 更新爱好", userId);
        userProfileService.updateHobbies(userId, hobbies);
        return Result.success();
    }
    
    /**
     * 更新 AI 身份类型
     */
    @PostMapping("/updateAIType")
    public Result<Void> updateAIType(@RequestBody java.util.Map<String, Object> body) {
        String userId = userContext.getUserId();
        Integer aiType = body.get("ai_type") != null ? Integer.parseInt(body.get("ai_type").toString()) : null;
        log.info("用户 {} 更新AI类型: {}", userId, aiType);
        userProfileService.updateAiType(userId, aiType);
        return Result.success();
    }
    
    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String userId = userContext.getUserId();
        log.info("用户上传头像，用户 ID: {}", userId);
        String avatarUrl = userProfileService.uploadAvatar(userId, file);
        return Result.success(avatarUrl);
    }
}
