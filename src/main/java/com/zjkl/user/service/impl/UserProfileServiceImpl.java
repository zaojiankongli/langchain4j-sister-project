package com.zjkl.user.service.impl;


import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.oss.service.OssService;
import com.zjkl.user.domain.User;
import com.zjkl.user.domain.dto.UserProfileUpdateDTO;
import com.zjkl.user.domain.vo.UserProfileVO;
import com.zjkl.user.mapper.UserProfileMapper;
import com.zjkl.user.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 用户资料服务实现
 */
@Service
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {
    
    private final UserProfileMapper userProfileMapper;
    private final OssService ossService;
    private final UserActivityTracker userActivityTracker;
    
    public UserProfileServiceImpl(UserProfileMapper userProfileMapper, OssService ossService,
                                  UserActivityTracker userActivityTracker) {
        this.userProfileMapper = userProfileMapper;
        this.ossService = ossService;
        this.userActivityTracker = userActivityTracker;
    }
    
    /**
     * 格式化 PAD 值，保留三位小数
     * 
     * @param value 原始值
     * @return 格式化后的值（保留三位小数）
     */
    private Double formatPadValue(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }
    
    @Override
    public UserProfileVO getProfile(String userId) {
        log.info("获取用户 {} 的资料", userId);
        
        UserProfileVO vo = new UserProfileVO();
        
        // 1. 基础用户信息
        User user = userProfileMapper.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在：" + userId);
        }
        BeanUtils.copyProperties(user, vo);
        // 补充 BeanUtils.copyProperties 未覆盖的字段
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setUserProfile(user.getUserProfile());
        // lastActiveAt：仅从 Redis 获取（实时），无值则返回 null
        Long redisActive = userActivityTracker.getLastActiveTime(userId);
        if (redisActive != null) {
            vo.setLastActiveAt(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(redisActive), ZoneId.systemDefault()));
        }
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        
        // 2. 等级经验
        UserProfileVO.LevelInfo levelInfo = userProfileMapper.findLevelInfo(userId);
        if (levelInfo != null) {
            vo.setCurrentLevel(levelInfo.getCurrentLevel());
            vo.setCurrentExp(levelInfo.getCurrentExp());
            vo.setLevelUpExp(levelInfo.getLevelUpExp());
            vo.setTotalExp(levelInfo.getTotalExp());
        } else {
            // 默认等级
            vo.setCurrentLevel(1);
            vo.setCurrentExp(0);
            vo.setLevelUpExp(100);
            vo.setTotalExp(0);
        }
        
        // 3. 情绪 PAD
        UserProfileVO.EmotionInfo emotionInfo = userProfileMapper.findLatestEmotion(userId);
        if (emotionInfo != null) {
            vo.setPleasure(formatPadValue(emotionInfo.getPleasure()));
            vo.setArousal(formatPadValue(emotionInfo.getArousal()));
            vo.setDominance(formatPadValue(emotionInfo.getDominance()));
            vo.setMoodDescription(emotionInfo.getMoodDescription());
        }
        
        // 4. AI 兴趣标签
        List<String> tags = userProfileMapper.findInterestTags(userId);
        vo.setInterestTags(tags);
        
        // 5. 聊天统计
        Integer messageCount = userProfileMapper.countMessages(userId);
        vo.setMessageCount(messageCount != null ? messageCount : 0);
        
        LocalDate firstChatDate = userProfileMapper.findFirstChatDate(userId);
        if (firstChatDate != null) {
            vo.setFirstChatTime(firstChatDate.atStartOfDay());
            // 计算相遇天数
            long meetDays = ChronoUnit.DAYS.between(firstChatDate, LocalDate.now());
            vo.setMeetDays((int) Math.max(0, meetDays));
        } else {
            vo.setFirstChatTime(null);
            vo.setMeetDays(0);
        }
        
        log.info("用户 {} 资料获取完成", userId);
        return vo;
    }
    
    @Override
    @Transactional
    public void updateProfile(String userId, UserProfileUpdateDTO dto) {
        log.info("更新用户 {} 的资料", userId);
        
        int rows = userProfileMapper.updateUserProfile(
            userId,
            dto.getUsername(),
            dto.getBirthday(),
            dto.getHobbies()
        );
        
        if (rows == 0) {
            throw new RuntimeException("更新用户资料失败");
        }
        
        log.info("用户 {} 资料更新成功", userId);
    }
    
    @Override
    @Transactional
    public void updateBasic(String userId, String username, Integer gender) {
        log.info("更新用户 {} 基本信息: username={}, gender={}", userId, username, gender);
        int rows = userProfileMapper.updateUserBasic(userId, username, gender);
        if (rows == 0) {
            throw new RuntimeException("更新基本信息失败");
        }
    }
    
    @Override
    @Transactional
    public void updateHobbies(String userId, String hobbies) {
        log.info("更新用户 {} 爱好", userId);
        int rows = userProfileMapper.updateUserHobbies(userId, hobbies);
        if (rows == 0) {
            throw new RuntimeException("更新爱好失败");
        }
    }
    
    @Override
    @Transactional
    public void updateAiType(String userId, Integer aiType) {
        log.info("更新用户 {} AI类型: {}", userId, aiType);
        int rows = userProfileMapper.updateUserAiType(userId, aiType);
        if (rows == 0) {
            throw new RuntimeException("更新AI类型失败");
        }
    }
    
    @Override
    @Transactional
    public String uploadAvatar(String userId, MultipartFile file) {
        // 参数校验
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空，请先登录");
        }
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        
        log.info("用户上传头像 - userId: {}, 文件名：{}, 大小：{} bytes", 
            userId, file.getOriginalFilename(), file.getSize());
        
        try {
            // 调用 OSS 服务上传头像
            String ossUrl = ossService.uploadAvatar(userId, file);
            
            // 更新数据库
            int rows = userProfileMapper.updateUserAvatar(userId, ossUrl);
            if (rows == 0) {
                throw new RuntimeException("更新头像失败：用户不存在");
            }
            
            log.info("用户 {} 头像上传成功：{}", userId, ossUrl);
            return ossUrl;
            
        } catch (IllegalArgumentException e) {
            // 参数校验失败，直接抛出
            log.warn("参数校验失败：{}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("上传头像失败 - userId: {}, filename: {}", userId, file.getOriginalFilename(), e);
            throw new RuntimeException("上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("上传头像异常 - userId: {}", userId, e);
            throw new RuntimeException("上传失败：" + e.getMessage());
        }
    }
}
