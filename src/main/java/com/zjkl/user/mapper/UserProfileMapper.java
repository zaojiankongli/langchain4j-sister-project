package com.zjkl.user.mapper;

import com.zjkl.user.domain.User;
import com.zjkl.user.domain.vo.UserProfileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户资料 Mapper
 */
@Mapper
public interface UserProfileMapper {
    
    // ========== 基础用户信息 ==========
    
    User findUserById(@Param("userId") String userId);
    
    int updateUserProfile(@Param("userId") String userId,
                          @Param("username") String username,
                          @Param("birthday") LocalDate birthday,
                          @Param("hobbies") String hobbies);
    
    int updateUserAvatar(@Param("userId") String userId, @Param("avatarUrl") String avatarUrl);
    
    int updateUserBasic(@Param("userId") String userId,
                        @Param("username") String username,
                        @Param("gender") Integer gender);
    
    int updateUserHobbies(@Param("userId") String userId, @Param("hobbies") String hobbies);
    
    int updateUserAiType(@Param("userId") String userId, @Param("aiType") Integer aiType);
    
    // ========== 等级经验 ==========
    
    UserProfileVO.LevelInfo findLevelInfo(@Param("userId") String userId);
    
    // ========== 情绪 PAD ==========
    
    UserProfileVO.EmotionInfo findLatestEmotion(@Param("userId") String userId);
    
    // ========== 兴趣标签 ==========

    List<String> findInterestTags(@Param("userId") String userId);

    /**
     * 新增兴趣标签
     * @return 影响行数
     */
    int insertInterestTag(@Param("userId") String userId, @Param("tagName") String tagName);

    /**
     * 软删除兴趣标签（将 is_deleted 设为 1）
     * @return 影响行数
     */
    int softDeleteInterestTag(@Param("userId") String userId, @Param("tagName") String tagName);

    /**
     * 批量软删除兴趣标签
     * @return 影响行数
     */
    int batchSoftDeleteInterestTags(@Param("userId") String userId, @Param("tagNames") List<String> tagNames);
    
    // ========== 聊天统计 ==========
    
    Integer countMessages(@Param("userId") String userId);
    
    LocalDate findFirstChatDate(@Param("userId") String userId);
}
