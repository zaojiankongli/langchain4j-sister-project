package com.zjkl.user.service;

import com.zjkl.auth.dto.CompleteProfileRequest;
import com.zjkl.user.domain.User;
import com.zjkl.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户资料管理服务（注册流程及资料完善）
 */
@Service
public class UserProfileManageService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileManageService.class);

    private final UserMapper userMapper;

    public UserProfileManageService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 完善个人资料（首次登录必填）
     *
     * @param userId  用户 ID
     * @param request 完善资料请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeProfile(String userId, CompleteProfileRequest request) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        log.info("开始完善用户资料 - userId: {}, username: {}, hasAvatar: {}",
            userId, request.username(), request.avatarUrl() != null && !request.avatarUrl().isBlank());

        if (request.username() != null && !request.username().isBlank()) {
            user.setUsername(request.username());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.hobbies() != null && !request.hobbies().isEmpty()) {
            String hobbiesStr = String.join(",", request.hobbies());
            user.setHobbies(hobbiesStr);
        }
        if (request.birthday() != null && !request.birthday().isBlank()) {
            try {
                user.setBirthday(LocalDate.parse(request.birthday()));
            } catch (Exception e) {
                // ignore invalid values
            }
        }
        if (request.aiType() != null) {
            user.setAiType(request.aiType());
            log.info("用户选择 AI 身份：{}", request.aiType());
        } else {
            throw new IllegalArgumentException("AI 身份不能为空");
        }

        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            user.setAvatarUrl(request.avatarUrl());
            log.info("用户设置头像：{}", request.avatarUrl());
        }

        userMapper.update(user);
        log.info("用户资料完善成功 - userId: {}, username: {}, avatarUrl: {}",
            userId, user.getUsername(), user.getAvatarUrl());
    }

    /**
     * 创建新用户
     */
    public User createUser(String email, String username) {
        User user = new User();
        user.setId(generateUserId());
        user.setEmail(email);
        user.setUsername(username);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    /**
     * 生成 12 位用户 ID
     */
    private String generateUserId() {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (userMapper.findById(id) == null) {
                return id;
            }
        }
        throw new RuntimeException("无法生成唯一用户 ID，请稍后重试");
    }

    /**
     * 构建用户信息
     */
    public Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("email", user.getEmail());
        info.put("username", user.getUsername());
        info.put("avatarUrl", user.getAvatarUrl());
        info.put("gender", user.getGender());
        info.put("hobbies", user.getHobbies());
        info.put("aiType", user.getAiType());
        info.put("birthday", user.getBirthday());
        info.put("createdAt", user.getCreatedAt());
        return info;
    }
}
