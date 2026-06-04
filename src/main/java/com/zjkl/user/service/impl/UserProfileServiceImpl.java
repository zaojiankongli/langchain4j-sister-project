package com.zjkl.user.service.impl;


import com.zjkl.ai.component.UserActivityTracker;
import com.zjkl.ai.oss.service.OssService;
import com.zjkl.common.ErrorCode;
import com.zjkl.common.exception.BusinessException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public String[] getProfileForChat(String userId) {
        User user = userProfileMapper.findUserById(userId);
        if (user == null) {
            return null;
        }
        return new String[]{
                user.getUsername(),
                user.getHobbies(),
                user.getUserProfile()
        };
    }

    @Override
    public UserProfileVO getProfile(String userId) {
        log.info("获取用户 {} 的资料", userId);
        
        UserProfileVO vo = new UserProfileVO();
        
        // 1. 基础用户信息（必须先执行，后续查询依赖 userId 存在性）
        User user = userProfileMapper.findUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        BeanUtils.copyProperties(user, vo);
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setUserProfile(user.getUserProfile());
        Long redisActive = userActivityTracker.getLastActiveTime(userId);
        if (redisActive != null) {
            vo.setLastActiveAt(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(redisActive), ZoneId.systemDefault()));
        }
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        
        // 2-5. 并行执行无依赖的查询（虚拟线程 + CompletableFuture）
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<UserProfileVO.LevelInfo> levelFuture = CompletableFuture.supplyAsync(
                    () -> userProfileMapper.findLevelInfo(userId), executor);
            CompletableFuture<UserProfileVO.EmotionInfo> emotionFuture = CompletableFuture.supplyAsync(
                    () -> userProfileMapper.findLatestEmotion(userId), executor);
            CompletableFuture<List<String>> tagsFuture = CompletableFuture.supplyAsync(
                    () -> userProfileMapper.findInterestTags(userId), executor);
            CompletableFuture<Integer> countFuture = CompletableFuture.supplyAsync(
                    () -> userProfileMapper.countMessages(userId), executor);
            CompletableFuture<LocalDate> firstDateFuture = CompletableFuture.supplyAsync(
                    () -> userProfileMapper.findFirstChatDate(userId), executor);

            CompletableFuture.allOf(levelFuture, emotionFuture, tagsFuture, countFuture, firstDateFuture).join();

            // 2. 等级经验
            UserProfileVO.LevelInfo levelInfo = levelFuture.join();
            if (levelInfo != null) {
                vo.setCurrentLevel(levelInfo.getCurrentLevel());
                vo.setCurrentExp(levelInfo.getCurrentExp());
                vo.setLevelUpExp(levelInfo.getLevelUpExp());
                vo.setTotalExp(levelInfo.getTotalExp());
            } else {
                vo.setCurrentLevel(1);
                vo.setCurrentExp(0);
                vo.setLevelUpExp(100);
                vo.setTotalExp(0);
            }

            // 3. 情绪 PAD
            UserProfileVO.EmotionInfo emotionInfo = emotionFuture.join();
            if (emotionInfo != null) {
                vo.setPleasure(formatPadValue(emotionInfo.getPleasure()));
                vo.setArousal(formatPadValue(emotionInfo.getArousal()));
                vo.setDominance(formatPadValue(emotionInfo.getDominance()));
                vo.setMoodDescription(emotionInfo.getMoodDescription());
            }

            // 4. AI 兴趣标签
            vo.setInterestTags(tagsFuture.join());

            // 5. 聊天统计
            Integer messageCount = countFuture.join();
            vo.setMessageCount(messageCount != null ? messageCount : 0);

            LocalDate firstChatDate = firstDateFuture.join();
            if (firstChatDate != null) {
                vo.setFirstChatTime(firstChatDate.atStartOfDay());
                long meetDays = ChronoUnit.DAYS.between(firstChatDate, LocalDate.now());
                vo.setMeetDays((int) Math.max(0, meetDays));
            } else {
                vo.setFirstChatTime(null);
                vo.setMeetDays(0);
            }
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), "更新用户资料失败");
        }
        
        log.info("用户 {} 资料更新成功", userId);
    }
    
    @Override
    @Transactional
    public void updateBasic(String userId, String username, Integer gender) {
        log.info("更新用户 {} 基本信息: username={}, gender={}", userId, username, gender);
        int rows = userProfileMapper.updateUserBasic(userId, username, gender);
        if (rows == 0) {
            throw new BusinessException("更新基本信息失败");
        }
    }
    
    @Override
    @Transactional
    public void updateHobbies(String userId, String hobbies) {
        log.info("更新用户 {} 爱好", userId);
        int rows = userProfileMapper.updateUserHobbies(userId, hobbies);
        if (rows == 0) {
            throw new BusinessException("更新爱好失败");
        }
    }
    
    @Override
    @Transactional
    public void updateAiType(String userId, Integer aiType) {
        log.info("更新用户 {} AI类型: {}", userId, aiType);
        int rows = userProfileMapper.updateUserAiType(userId, aiType);
        if (rows == 0) {
            throw new BusinessException("更新AI类型失败");
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
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            
            log.info("用户 {} 头像上传成功：{}", userId, ossUrl);
            return ossUrl;
            
        } catch (IllegalArgumentException e) {
            // 参数校验失败，直接抛出
            log.warn("参数校验失败：{}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("上传头像失败 - userId: {}, filename: {}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED.getCode(), "上传头像失败，请稍后重试");
        } catch (Exception e) {
            log.error("上传头像异常 - userId: {}", userId, e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED.getCode(), "上传头像失败");
        }
    }
}
